package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.WarpLoader;
import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.mojang.brigadier.Command;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static boolean warpExists(String warpName) {
        return store.exists(warpName);
    }

    public static @Nullable Warp getWarp(String warpName) {
        return store.get(warpName);
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

    public static int updateWarp(String warpName, Consumer<Warp> updater, Player player, String successMsg) {
        try {
            store.update(warpName, updater);
            player.sendRichMessage("<green>" + successMsg);
            return Command.SINGLE_SUCCESS;
        } catch (WarpStore.WarpStoreException | IllegalArgumentException e) {
            player.sendRichMessage("<red>" + e.getMessage());
            return 0;
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

    public static void saveAllWarps() {
        for (Warp w : store.all()) {
            try {
                store.save(w);
            } catch (WarpStore.WarpStoreException e) {
                WarpVelocity.getLogger().error("Failed to save warp {} - {}", w.getName(), e.getMessage());
            }
        }
    }

    public static void saveWarp(Warp warp) throws Exception {
        try {
            store.save(warp);
        } catch (WarpStore.WarpStoreException e) {
            WarpVelocity.getLogger()
                .error("An error occurred whilst saving warp {} - {}", warp.getName(), e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    public static void saveWarpVisits() {
        for (Warp w : store.all()) {
            Path path = getWarpPath(w);
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(path).build();

            if (!Files.exists(path)) {
                // This is only saving the visits field, so if the warp no longer exists
                // don't save to it
                WarpVelocity.getLogger().error(
                    "Failed to update the warp count for warp {} - there exists no warp file at {}",
                    w.getName(), path
                );
                continue;
            }

            try {
                ConfigurationNode root = loader.load();
                root.node("visits").set(w.getVisits());
                loader.save(root);
            } catch (IOException e) {
                WarpVelocity.getLogger().error(
                    "An error occurred whilst saving warp {} - {}",
                    w.getName(), e.getMessage()
                );
            }
        }
    }

    public static void loadAllWarps() {
        store.clear();

        if (!Files.exists(WARPS_DIRECTORY)) {
            WarpVelocity.getLogger().warn("The warps directory does not exist ({}), attempting to load from the warps DB", WARPS_DIRECTORY);

            var DB = new MyWarpDBConnector();
            if (!DB.isConnected()) {
                WarpVelocity.getLogger().error("Unable to connect to the warps DB, skipping warp loading");
                return;
            }

            int counter = 0;
            var dbWarps = DB.getWarps();
            for (Warp w : dbWarps) {
                try {
                    store.registerLoaded(w);
                    counter++;
                } catch (Exception e) {
                    WarpVelocity.getLogger().error("Failed to load warp, {}", e.getMessage());
                }
            }
            DB.disconnect();

            logLoadSummary(counter, dbWarps.size());
            return;
        }

        try (Stream<Path> pathStream = Files.walk(WARPS_DIRECTORY)) {
            int counter = 0;
            List<Path> yamlFiles = pathStream
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".yml"))
                .toList();

            for (Path file : yamlFiles) {
                try {
                    store.registerLoaded(loadWarp(file));
                    counter++;
                } catch (Exception e) {
                    WarpVelocity.getLogger().error("Failed to load warp at {} - {}", file, e.getMessage());
                }
            }

            logLoadSummary(counter, yamlFiles.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan warp directory: " + WARPS_DIRECTORY, e);
        }
    }

    private static void logLoadSummary(int counter, int total) {
        long publicCount = store.all().stream().filter(w -> w.isOfType(Warp.Type.PUBLIC)).count();
        WarpVelocity.getLogger().info("""
            \nLoaded public warps: {}
            Loaded private warps: {}
            Total loaded: {}/{}
            """, publicCount, store.size() - publicCount, counter, total);
    }

    public static Warp loadWarp(Path filePath) throws ConfigurateException {
        ConfigurationNode root = WarpLoader.build(filePath).load();
        return root.get(Warp.class);
    }

    // Utils
    public static String normaliseWarpName(String warpName) {
        return WarpPaths.normaliseWarpName(warpName);
    }

    private static Path getWarpPath(Warp warp) {
        return WarpPaths.resolveWarpPath(WARPS_DIRECTORY, warp);
    }
}
