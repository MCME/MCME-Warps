package com.mcmiddleearth.warps.velocity.warps;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Owns the in-memory warps and all persistence.
 * <p>
 * <b>Namespacing (root-fix #1):</b> public warps are keyed by normalised name in one global map
 * (they are curated, network-unique landmarks); private warps are keyed by {@code (creatorId, name)}
 * so two players may each own a {@code home}. A bare-name lookup ({@code /warp <name>}) resolves the
 * sender's own private warp first, then falls through to public.
 * <p>
 * <b>Thread safety (CONC-1):</b> every map is a {@link ConcurrentHashMap} and compound operations use
 * atomic primitives. <b>Crash safety (CORR-A / CORR-B):</b> a warp is only added/updated in memory
 * after its file is written, and only removed after its file is deleted.
 * <p>
 * <b>Atomic reload (root cause #3b):</b> both maps live behind a single {@code volatile} snapshot, so
 * a reload builds a fresh snapshot off to the side and swaps it in with one reference write. A lookup
 * therefore always sees either the whole previous set or the whole new set - never the empty/partial
 * window the old clear-then-refill left open.
 * <p>
 * Takes its warps directory and {@link WarpFileIo} by constructor so it can be unit tested.
 */
public class WarpStore {

    /** Thrown when a store operation cannot be completed; the caller turns it into a user message. */
    public static class WarpStoreException extends Exception {
        public WarpStoreException(String message) { super(message); }
    }

    /** The public + private maps as one swappable unit (root cause #3b). */
    private record Snapshot(Map<String, Warp> publicWarps, Map<UUID, Map<String, Warp>> privateWarps) {
        static Snapshot empty() {
            return new Snapshot(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        }
    }

    private final Path warpsDir;
    private final WarpFileIo io;

    /** Swapped atomically on reload; read once per operation. Live mutations act on the current one. */
    private volatile Snapshot snapshot = Snapshot.empty();

    /** Warps whose in-memory visit count has changed since the last flush (root cause #3a). */
    private final Set<Warp> visited = ConcurrentHashMap.newKeySet();

    public WarpStore(Path warpsDir, WarpFileIo io) {
        this.warpsDir = warpsDir;
        this.io = io;
    }

    private static String key(String name) { return WarpPaths.normaliseWarpName(name); }

    /** The map (within {@code s}) a warp lives in, creating the per-creator map on demand for a private warp. */
    private static Map<String, Warp> mapFor(Snapshot s, Warp warp) {
        return warp.isOfType(Warp.Type.PUBLIC)
            ? s.publicWarps()
            : s.privateWarps().computeIfAbsent(warp.getCreatorId(), k -> new ConcurrentHashMap<>());
    }

    private static Stream<Warp> allStream(Snapshot s) {
        return Stream.concat(s.publicWarps().values().stream(),
            s.privateWarps().values().stream().flatMap(m -> m.values().stream()));
    }

    // --- lookup ------------------------------------------------------------

    public @Nullable Warp getPublic(String name) {
        return snapshot.publicWarps().get(key(name));
    }

    public @Nullable Warp getPrivate(UUID creator, String name) {
        Map<String, Warp> m = snapshot.privateWarps().get(creator);
        return m == null ? null : m.get(key(name));
    }

    /** Resolve a bare name for {@code sender}: their own private warp shadows a public one. */
    public @Nullable Warp resolve(String name, @Nullable UUID sender) {
        if (sender != null) {
            Warp priv = getPrivate(sender, name);
            if (priv != null) return priv;
        }
        return getPublic(name);
    }

    public boolean publicExists(String name) {
        return snapshot.publicWarps().containsKey(key(name));
    }

    public boolean privateExists(UUID creator, String name) {
        Map<String, Warp> m = snapshot.privateWarps().get(creator);
        return m != null && m.containsKey(key(name));
    }

    public Map<String, Warp> getWarps(Predicate<Warp> filter) {
        // Keyed by normalised name; a rare same-name clash in the filtered view keeps the first.
        return allStream(snapshot).filter(filter)
            .collect(Collectors.toMap(w -> key(w.getName()), w -> w, (a, b) -> a));
    }

    public Map<String, String> getWarpNames(Predicate<Warp> filter) {
        return allStream(snapshot).filter(filter)
            .collect(Collectors.toMap(w -> key(w.getName()), Warp::getName, (a, b) -> a));
    }

    public Collection<Warp> all() {
        return allStream(snapshot).collect(Collectors.toList());
    }

    public int size() {
        Snapshot s = snapshot;
        return s.publicWarps().size() + s.privateWarps().values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Atomically replaces the store contents with {@code loaded} (root cause #3b): builds a fresh
     * snapshot off to the side and swaps it in with a single reference write, so lookups never see a
     * partial map. A warp whose {@code (type, creator, name)} key collides with an earlier one is
     * skipped and reported to {@code onSkipped} (the loader logs it with the offending file). Pending
     * visit-dirty state from the previous snapshot is discarded.
     */
    public void replaceAll(Collection<Warp> loaded, BiConsumer<Warp, String> onSkipped) {
        Snapshot fresh = Snapshot.empty();
        for (Warp w : loaded) {
            Warp prev = mapFor(fresh, w).putIfAbsent(key(w.getName()), w);
            if (prev != null) {
                onSkipped.accept(w, "duplicate " + (w.isOfType(Warp.Type.PUBLIC) ? "public" : "private")
                    + " warp key '" + key(w.getName()) + "'");
            }
        }
        this.snapshot = fresh;
        this.visited.clear();
    }

    // --- visit tracking (root cause #3a) -----------------------------------

    /**
     * Records a teleport to {@code warp}: bumps its visit count in memory and marks it for the next
     * {@link #flushVisits}, so a hot warp is not written to disk on every single teleport.
     */
    public void recordVisit(Warp warp) {
        warp.addVisit();
        visited.add(warp);
    }

    /**
     * Persists the visit counts of every warp visited since the last flush, through the same crash-safe
     * {@link #save} seam as every other write. This replaces the old path that load-modify-saved each
     * YAML file directly (bypassing the seam and racing full-object updates - root cause #3). Only
     * visited warps are written, so the flush stays cheap even with tens of thousands of warps on disk.
     * <p>
     * Each dirty reference is resolved to its CURRENT stored object first, so an {@link #update}'s copy
     * is written rather than a stale reference, and a warp {@link #delete}d after being visited is
     * simply skipped. Per-warp write failures are reported to {@code onError} instead of aborting.
     *
     * @return the number of warps actually written
     */
    public int flushVisits(BiConsumer<Warp, Exception> onError) {
        // Drain the dirty set, de-duplicating by current identity (several dirty references can resolve
        // to the same live object after an update).
        Set<Warp> toFlush = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Iterator<Warp> it = visited.iterator(); it.hasNext(); ) {
            Warp ref = it.next();
            it.remove();
            Warp current = ref.isOfType(Warp.Type.PUBLIC)
                ? getPublic(ref.getName())
                : getPrivate(ref.getCreatorId(), ref.getName());
            if (current != null) {
                toFlush.add(current);
            }
        }

        int flushed = 0;
        for (Warp warp : toFlush) {
            try {
                save(warp);
                flushed++;
            } catch (WarpStoreException e) {
                onError.accept(warp, e);
            }
        }
        return flushed;
    }

    // --- mutations ---------------------------------------------------------

    /** Crash-safe add: reserve the map slot atomically, then write the file; undo on write failure. */
    public void add(Warp warp) throws WarpStoreException {
        String k = key(warp.getName());
        Path path = WarpPaths.resolveWarpPath(warpsDir, warp); // may throw IllegalArgumentException (SEC-1)
        Map<String, Warp> map = mapFor(snapshot, warp);

        Warp prev = map.putIfAbsent(k, warp);
        if (prev != null) {
            throw new WarpStoreException("A warp already exists with name '" + k + "'");
        }
        try {
            io.write(path, warp);
        } catch (IOException e) {
            map.remove(k, warp); // undo the reservation - no orphan in memory
            throw new WarpStoreException("Failed to save warp '" + warp.getName() + "' to disk");
        }
    }

    /** Overwrites an existing warp's file (used by in-place setters and the visit flush). */
    public void save(Warp warp) throws WarpStoreException {
        Path path = WarpPaths.resolveWarpPath(warpsDir, warp);
        try {
            io.write(path, warp);
        } catch (IOException e) {
            throw new WarpStoreException("Failed to save warp '" + warp.getName() + "' to disk");
        }
    }

    /**
     * Crash-safe update (CORR-A): applies {@code updater} to a copy of {@code current}, writes the new
     * file, swaps the map(s), then deletes the old file. A failed write leaves the live warp and its
     * file untouched. Handles a type change (setPublic/setPrivate) that moves the warp between the
     * public and private maps, with a target-scope duplicate check.
     */
    public void update(Warp current, Consumer<Warp> updater) throws WarpStoreException {
        Warp mutated = new Warp(current);
        updater.accept(mutated);

        Snapshot s = snapshot;
        Map<String, Warp> oldMap = mapFor(s, current);
        Map<String, Warp> newMap = mapFor(s, mutated);
        String oldKey = key(current.getName());
        String newKey = key(mutated.getName());

        Path oldPath = WarpPaths.resolveWarpPath(warpsDir, current);
        Path newPath = WarpPaths.resolveWarpPath(warpsDir, mutated); // may throw (SEC-1)

        boolean moved = (oldMap != newMap) || !oldKey.equals(newKey);
        if (moved && newMap.containsKey(newKey)) {
            throw new WarpStoreException("A warp already exists with name '" + newKey + "'");
        }

        // 1. write the new file first - if this throws, nothing below runs and the original stands.
        try {
            io.write(newPath, mutated);
        } catch (IOException e) {
            throw new WarpStoreException("Failed to save warp '" + mutated.getName() + "' to disk");
        }

        // 2. swap the map(s)
        newMap.put(newKey, mutated);
        if (moved) {
            oldMap.remove(oldKey);
        }

        // 3. delete the old file last, only if the path actually changed.
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
        mapFor(snapshot, warp).remove(key(warp.getName()));
    }
}
