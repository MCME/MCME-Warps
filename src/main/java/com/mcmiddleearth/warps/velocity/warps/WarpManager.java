package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    public static List<String> getAllWarpNames() {
        return warps.values().stream().map(Warp::getName).toList();
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
            throw new Exception("Warp already exists");
        }
        warps.put(warpName, warp);
    }

    public static boolean addWarp(Warp newWarp) {
        try {
            putWarp(newWarp);
            saveWarp(newWarp);
            return true;
        } catch (Exception e) {
            WarpVelocity.getInstance().getLogger().error("Failed to add warp {} - {}", newWarp.getName(), e.getMessage());
            return false;
        }
    }

    public static void deleteWarp(Warp warp) {
        String warpName = normalise(warp.getName());
        warps.remove(warpName);

        Path warpPath = getWarpPath(warp);
        try {
            Files.deleteIfExists(warpPath);
        } catch (IOException e) {
            WarpVelocity.getInstance().getLogger()
                .error("An error occurred whilst deleting the warp file at {} - {}",
                    warpPath, e.getMessage()
                );
        }
    };

    public static void saveWarp(Warp warp) {
        Path path = getWarpPath(warp);
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(path).build();

        try {
            Files.createDirectories(path.getParent());

            ConfigurationNode root = loader.load();
            root.set(Warp.class, warp);
            loader.save(root);
        } catch (IOException e) {
            WarpVelocity.getInstance().getLogger()
                .error( "An error occurred whilst saving warp {} - {}",
                    warp.getName(), e.getMessage()
                );
        }
    }

    public static void loadAllWarps() {
        if (!Files.exists(WARPS_DIRECTORY)) {
            WarpVelocity.getInstance().getLogger().warn("The warps directory does not exist ({}), skipping warp loading", WARPS_DIRECTORY);
            return;
        }

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
        return name.trim().toLowerCase(Locale.ROOT);
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
