package com.mcmiddleearth.warps.velocity.warps;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Owns the in-memory warp map and all persistence, with two guarantees the old static
 * {@code WarpManager} lacked:
 * <ul>
 *   <li><b>Thread safety (CONC-1):</b> the map is a {@link ConcurrentHashMap} and compound
 *       operations use atomic primitives, so concurrent commands / async suggestions / message
 *       events cannot corrupt it or throw {@code ConcurrentModificationException}.</li>
 *   <li><b>Crash safety (CORR-A / CORR-B):</b> a warp is only added/updated in memory <em>after</em>
 *       its file is written, and only removed from memory <em>after</em> its file is deleted - so a
 *       failed disk write never loses a warp or leaves an orphan, and a failed delete never drops a
 *       warp from memory while its file survives to resurrect on reload.</li>
 * </ul>
 * Takes its warps directory and {@link WarpFileIo} by constructor so it can be unit tested.
 */
public class WarpStore {

    /** Thrown when a store operation cannot be completed; the caller turns it into a user message. */
    public static class WarpStoreException extends Exception {
        public WarpStoreException(String message) { super(message); }
    }

    private final Path warpsDir;
    private final WarpFileIo io;
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();

    public WarpStore(Path warpsDir, WarpFileIo io) {
        this.warpsDir = warpsDir;
        this.io = io;
    }

    // --- queries -----------------------------------------------------------

    public boolean exists(String warpName) {
        return warps.containsKey(WarpPaths.normaliseWarpName(warpName));
    }

    public @Nullable Warp get(String warpName) {
        return warps.get(WarpPaths.normaliseWarpName(warpName));
    }

    public Map<String, Warp> getWarps(Predicate<Warp> filter) {
        return warps.entrySet().stream()
            .filter(e -> filter.test(e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, String> getWarpNames(Predicate<Warp> filter) {
        return warps.entrySet().stream()
            .filter(e -> filter.test(e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));
    }

    public Collection<Warp> all() {
        return warps.values();
    }

    public int size() {
        return warps.size();
    }

    public void clear() {
        warps.clear();
    }

    // --- mutations ---------------------------------------------------------

    /** Registers a warp already on disk (during load) - no file write, but still atomic + dup-checked. */
    public void registerLoaded(Warp warp) throws WarpStoreException {
        String key = WarpPaths.normaliseWarpName(warp.getName());
        Warp prev = warps.putIfAbsent(key, warp);
        if (prev != null) {
            throw new WarpStoreException("A warp already exists with name '" + key + "'");
        }
    }

    /** Crash-safe add: reserve the map slot atomically, then write the file; undo on write failure. */
    public void add(Warp warp) throws WarpStoreException {
        String key = WarpPaths.normaliseWarpName(warp.getName());
        Path path = WarpPaths.resolveWarpPath(warpsDir, warp); // may throw IllegalArgumentException (SEC-1)

        // Reserve the slot atomically first (CONC-1) so two concurrent adds cannot both proceed.
        Warp prev = warps.putIfAbsent(key, warp);
        if (prev != null) {
            throw new WarpStoreException("A warp already exists with name '" + key + "'");
        }
        try {
            io.write(path, warp);
        } catch (IOException e) {
            warps.remove(key, warp); // undo the reservation - no orphan in memory
            throw new WarpStoreException("Failed to save warp '" + warp.getName() + "' to disk");
        }
    }

    /** Overwrites an existing warp's file (used by in-place setters). */
    public void save(Warp warp) throws WarpStoreException {
        Path path = WarpPaths.resolveWarpPath(warpsDir, warp);
        try {
            io.write(path, warp);
        } catch (IOException e) {
            throw new WarpStoreException("Failed to save warp '" + warp.getName() + "' to disk");
        }
    }

    /**
     * Crash-safe update (CORR-A): applies {@code updater} to a <em>copy</em>, writes the new file,
     * swaps the map, then deletes the old file. If the write fails the live warp and its file are
     * left completely untouched - no permanent loss, no in-memory duplicate.
     */
    public void update(String warpName, Consumer<Warp> updater) throws WarpStoreException {
        String key = WarpPaths.normaliseWarpName(warpName);
        Warp current = warps.get(key);
        if (current == null) {
            throw new WarpStoreException("Warp '" + warpName + "' does not exist");
        }

        Warp mutated = new Warp(current);
        updater.accept(mutated);
        String newKey = WarpPaths.normaliseWarpName(mutated.getName());

        Path oldPath = WarpPaths.resolveWarpPath(warpsDir, current);
        Path newPath = WarpPaths.resolveWarpPath(warpsDir, mutated); // may throw (SEC-1)

        boolean rename = !newKey.equals(key);
        if (rename && warps.containsKey(newKey)) {
            throw new WarpStoreException("A warp already exists with name '" + newKey + "'");
        }

        // 1. write the new file first - if this throws, nothing below runs and the original stands.
        try {
            io.write(newPath, mutated);
        } catch (IOException e) {
            throw new WarpStoreException("Failed to save warp '" + mutated.getName() + "' to disk");
        }

        // 2. swap the map
        warps.put(newKey, mutated);
        if (rename) {
            warps.remove(key);
        }

        // 3. delete the old file last, only if the path actually changed. A failure here leaves a
        //    harmless leftover file, not data loss - the new file and map are already consistent.
        if (!newPath.equals(oldPath)) {
            try {
                io.deleteIfExists(oldPath);
            } catch (IOException ignored) {
                // leftover old file; not fatal
            }
        }
    }

    /**
     * Crash-safe delete (CORR-B): delete the file first; only forget the warp once the file is
     * actually gone, so a drifted/stale on-disk file cannot resurrect a "deleted" warp on reload.
     */
    public void delete(Warp warp) throws WarpStoreException {
        String key = WarpPaths.normaliseWarpName(warp.getName());
        Path path = WarpPaths.resolveWarpPath(warpsDir, warp);

        boolean deleted;
        try {
            deleted = io.deleteIfExists(path);
        } catch (IOException e) {
            throw new WarpStoreException("Failed to delete the warp file for '" + warp.getName() + "'");
        }
        if (!deleted) {
            throw new WarpStoreException(
                "No warp file found at the expected path for '" + warp.getName() + "' - left in memory");
        }
        warps.remove(key);
    }
}
