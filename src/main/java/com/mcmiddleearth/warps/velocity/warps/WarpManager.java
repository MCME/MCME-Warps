package com.mcmiddleearth.warps.velocity.warps;

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
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class WarpManager {
    private static final Path WARPS_DIRECTORY =  WarpVelocity.getInstance().getDataFolder().resolve("warps");

    private static final HashMap<String, Warp> warps = new HashMap<>();

    public static boolean warpExists(String warpName) {
        return warps.containsKey(normalise(warpName));
    }

    public static @Nullable Warp getWarp(String warpName) {
        String normalisedWarpName = normalise(warpName);

        if (warpExists(warpName)) {
            return warps.get(normalisedWarpName);
        }

        return null;
    }

    public static List<String> getWarpNames(Predicate<Warp> filter) {
        return warps.values().stream().filter(filter).map(Warp::getName).toList();
    }
    public static List<String> getAllModifiableWarpNames(Player player) {
        return warps.values().stream().filter(warp -> warp.isModifiable(player)).map(Warp::getName).toList();
    }
    public static List<String> getAllUsableWarpNames(Player player) {
        return warps.values().stream().filter(warp -> warp.isUsable(player)).map(Warp::getName).toList();
    }

    private static void putWarp(Warp warp) throws Exception {
        String warpName = normalise(warp.getName());

        if (warps.containsKey(warpName)) {
            throw new Exception("A warp already exists with name '" + warpName + "'");
        }
        warps.put(warpName, warp);
    }

    public static void addWarp(Warp newWarp) throws IllegalStateException {
        try {
            putWarp(newWarp);
            saveWarp(newWarp);
        } catch (Exception e) {
            WarpVelocity.getInstance().getLogger().error("Failed to add warp {} - {}", newWarp.getName(), e.getMessage());
            throw new IllegalStateException(e.getMessage());
        }
    }

    public static int updateWarp(String warpName, Consumer<Warp> updater, Player player, String successMsg) {
        Warp warp = getWarp(warpName);

        if (warp == null) {
            player.sendRichMessage("<red>Warp '%s' does not exist, unable to perform the update".formatted(warpName));
            return 0;
        }

        Warp preUpdateWarp = new Warp(warp);

        try {
            deleteWarp(warp);
        } catch (Exception e) {
            player.sendRichMessage("<red>" + e.getMessage());
            return 0;
        }

        updater.accept(warp);

        try {
            addWarp(warp);
            player.sendRichMessage("<green>" + successMsg);
            return Command.SINGLE_SUCCESS;
        } catch (IllegalStateException e) {
            player.sendRichMessage("<red>" + e.getMessage());
            player.sendRichMessage("<red>Failed to perform the update, rolling back...");

            // If the put succeeded but the save failed this rollback won't
            // remove the erroneous new Warp, but this shouldn't be common

            try {
                addWarp(preUpdateWarp);
            } catch (Exception addOldWarpException) {
                player.sendRichMessage("<red>Rollback failed - " + e.getMessage());
                return 0;
            }

            player.sendRichMessage("<red>Rollback successful");
            return 0;
        }
    }

    public static void deleteWarp(Warp warp) throws Exception {
        String warpName = normalise(warp.getName());
        warps.remove(warpName);

        Path warpPath = getWarpPath(warp);
        try {
            boolean result = Files.deleteIfExists(warpPath);
            if (!result) {
                throw new Exception("File does not exist");
            }
        } catch (Exception e) {
            WarpVelocity.getInstance().getLogger()
                .error("An error occurred whilst deleting the warp file at {} - {}",
                    warpPath, e.getMessage()
                );
            throw new Exception("An error occurred whilst deleting the warp file for " + warpName);
        }
    };

    public static void saveAllWarps() {
        for (Warp w: warps.values()) {
            try {
                saveWarp(w);
            } catch (Exception e) {
                WarpVelocity.getInstance().getLogger().error(
                    "Failed to save warp {} - {}", w.getName(), e.getMessage()
                );
            }
        }
    }

    public static void saveWarp(Warp warp) throws Exception {
        Path path = getWarpPath(warp);
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(path).build();

        try {
            Files.createDirectories(path.getParent());

            ConfigurationNode root = loader.load();
            root.set(Warp.class, warp);
            loader.save(root);
        } catch (IOException e) {
            WarpVelocity.getInstance().getLogger()
                .error("An error occurred whilst saving warp {} - {}",
                    warp.getName(), e.getMessage()
                );
            throw new Exception("Failed to save warp '%s' to disk".formatted(warp.getName()));
        }
    }

    public static void saveWarpVisits() {
        for (Warp w: warps.values()) {
            Path path = getWarpPath(w);
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(path).build();

            if (!Files.exists(path)) {
                // This is only saving the visits field, so if the warp no longer exists
                // don't save to it
                WarpVelocity.getInstance().getLogger().error(
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
                WarpVelocity.getInstance().getLogger().error(
                    "An error occurred whilst saving warp {} - {}",
                    w.getName(), e.getMessage()
                );
            }
        }
    }

    public static void loadAllWarps() {
        if (!Files.exists(WARPS_DIRECTORY)) {
            WarpVelocity.getInstance().getLogger().warn("The warps directory does not exist ({}), skipping warp loading", WARPS_DIRECTORY);
            return;
        }

        warps.clear();

        try (Stream<Path> pathStream = Files.walk(WARPS_DIRECTORY)) {
            List<Path> yamlFiles = pathStream
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".yml"))
                .toList();

            for (Path file : yamlFiles) {
                try {
                    Warp warp = loadWarp(file);
                    putWarp(warp);
                } catch (Exception e) {
                    WarpVelocity.getInstance().getLogger().error("Failed to load warp at {} - {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan warp directory: " + WARPS_DIRECTORY, e);
        }
    }

    public static Warp loadWarp(Path filePath) throws ConfigurateException {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
            .path(filePath)
            .build();

        ConfigurationNode root = loader.load();
        return root.get(Warp.class);
    }

    // Utils
    private static String normalise(String name) {
        // Removing apostrophes so that a player isn't forced to type one
        return name.trim().replace("'", "").toLowerCase(Locale.ROOT);
    }

    private static Path getWarpPath(Warp warp) {
        String warpName = normalise(warp.getName());

        if (warp.isOfType(Warp.Type.PUBLIC)) {
            return WARPS_DIRECTORY
                .resolve(warp.getServer())
                .resolve(warp.getLocation().world())
                .resolve(warpName + ".yml");
        }

        return WARPS_DIRECTORY
            .resolve(warp.getCreator().toString())
            .resolve(warpName + ".yml");
    }
}
