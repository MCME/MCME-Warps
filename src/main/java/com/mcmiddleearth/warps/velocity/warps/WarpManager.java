package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.WarpLoader;
import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.mojang.brigadier.Command;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Static facade over a single {@link WarpStore}. The map, its thread safety (CONC-1) and the
 * crash-safe persistence (CORR-A / CORR-B) all live in the store; this class keeps the existing
 * static API the commands and listeners call, and owns the proxy-coupled concerns: the data folder,
 * logging, the file-walk load and the legacy MyWarp DB import.
 */
public class WarpManager {
    private static final Path WARPS_DIRECTORY = WarpVelocity.getDataFolder().resolve("warps");
    private static final WarpStore store = new WarpStore(WARPS_DIRECTORY, new ConfigurateWarpIo());

    public static boolean publicWarpExists(String warpName) {
        return store.publicExists(warpName);
    }

    public static boolean privateWarpExists(java.util.UUID creator, String warpName) {
        return store.privateExists(creator, warpName);
    }

    /** Resolve a bare name for {@code sender}: their own private warp shadows a public one (root-fix #1). */
    public static @Nullable Warp resolveWarp(String warpName, @Nullable java.util.UUID sender) {
        return store.resolve(warpName, sender);
    }

    public static @Nullable Warp getPublicWarp(String warpName) {
        return store.getPublic(warpName);
    }

    public static @Nullable Warp getPrivateWarp(java.util.UUID creator, String warpName) {
        return store.getPrivate(creator, warpName);
    }

    public static Map<String, String> getWarpNames(Predicate<Warp> filter) {
        return store.getWarpNames(filter);
    }

    public static Map<String, Warp> getWarps(Predicate<Warp> filter) {
        return store.getWarps(filter);
    }

    public static void addWarp(Warp newWarp) throws IllegalStateException {
        try {
            store.add(newWarp);
        } catch (WarpStore.WarpStoreException | IllegalArgumentException e) {
            WarpVelocity.getLogger().error("Failed to add warp {} - {}", newWarp.getName(), e.getMessage());
            throw new IllegalStateException(e.getMessage());
        }
    }

    public static int updateWarp(Warp warp, Consumer<Warp> updater, Player player, String successMsg) {
        try {
            store.update(warp, updater);
            player.sendRichMessage("<green>" + successMsg);
            return Command.SINGLE_SUCCESS;
        } catch (WarpStore.WarpStoreException | IllegalArgumentException e) {
            player.sendRichMessage("<red>" + e.getMessage());
            return 0;
        }
    }

    /**
     * Crash-safe update with no player messaging (root cause #3): for callers that do their own
     * messaging. Routes the change through {@link WarpStore#update} - mutating a copy, writing it, then
     * swapping - so a failed write leaves the live warp and its file untouched.
     *
     * @return whether the update succeeded
     */
    public static boolean updateWarp(Warp warp, Consumer<Warp> updater) {
        try {
            store.update(warp, updater);
            return true;
        } catch (WarpStore.WarpStoreException | IllegalArgumentException e) {
            WarpVelocity.getLogger().error("Failed to update warp {} - {}", warp.getName(), e.getMessage());
            return false;
        }
    }

    public static void deleteWarp(Warp warp) throws Exception {
        try {
            store.delete(warp);
        } catch (WarpStore.WarpStoreException e) {
            WarpVelocity.getLogger()
                .error("An error occurred whilst deleting the warp file for {} - {}", warp.getName(), e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    /** Records a teleport so the warp's visit count is flushed on the next {@link #saveWarpVisits}. */
    public static void recordVisit(Warp warp) {
        store.recordVisit(warp);
    }

    /**
     * Flushes the visit counts of warps visited since the last flush, through the crash-safe store
     * seam (root cause #3). Only visited warps are written, so this is cheap even with tens of
     * thousands of warps on disk - unlike the old path, which re-read and re-wrote every file and
     * could clobber a concurrent full-warp update.
     */
    public static void saveWarpVisits() {
        store.flushVisits((warp, error) -> WarpVelocity.getLogger()
            .error("Failed to save the visit count for warp {} - {}", warp.getName(), error.getMessage()));
    }

    /**
     * Loads all warps into a fresh set off to the side, then swaps it into the store atomically (root
     * cause #3b). The store is never emptied first, so lookups during a reload always see either the
     * whole previous set or the whole new one - and a load that fails outright leaves the live warps
     * untouched rather than wiping them.
     */
    public static void loadAllWarps() {
        if (!Files.exists(WARPS_DIRECTORY)) {
            loadAllWarpsFromDatabase();
            return;
        }

        List<Warp> loaded = new ArrayList<>();
        int totalFiles;
        try (Stream<Path> pathStream = Files.walk(WARPS_DIRECTORY)) {
            List<Path> yamlFiles = pathStream
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".yml"))
                .toList();
            totalFiles = yamlFiles.size();

            for (Path file : yamlFiles) {
                try {
                    loaded.add(loadWarp(file));
                } catch (Exception e) {
                    WarpVelocity.getLogger().error("Failed to load warp at {} - {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan warp directory: " + WARPS_DIRECTORY, e);
        }

        installLoadedWarps(loaded, totalFiles);
    }

    private static void loadAllWarpsFromDatabase() {
        WarpVelocity.getLogger().warn("The warps directory does not exist ({}), attempting to load from the warps DB", WARPS_DIRECTORY);

        var DB = new MyWarpDBConnector();
        if (!DB.isConnected()) {
            WarpVelocity.getLogger().error("Unable to connect to the warps DB, skipping warp loading");
            return;
        }

        Collection<Warp> dbWarps = DB.getWarps();
        DB.disconnect();

        installLoadedWarps(dbWarps, dbWarps.size());
    }

    /** Swaps the parsed warps into the store atomically and logs a load summary. */
    private static void installLoadedWarps(Collection<Warp> loaded, int totalFiles) {
        store.replaceAll(loaded, (warp, why) ->
            WarpVelocity.getLogger().error("Skipped warp '{}' during load - {}", warp.getName(), why));

        long publicCount = store.all().stream().filter(w -> w.isOfType(Warp.Type.PUBLIC)).count();
        int installed = store.size();
        WarpVelocity.getLogger().info("""
            \nLoaded public warps: {}
            Loaded private warps: {}
            Total loaded: {}/{}
            """, publicCount, installed - publicCount, installed, totalFiles);
    }

    public static Warp loadWarp(Path filePath) throws ConfigurateException {
        ConfigurationNode root = WarpLoader.build(filePath).load();
        return root.get(Warp.class);
    }

    // Utils
    public static String normaliseWarpName(String warpName) {
        return WarpPaths.normaliseWarpName(warpName);
    }
}
