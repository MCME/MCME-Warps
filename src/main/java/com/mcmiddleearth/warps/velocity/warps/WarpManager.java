package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.velocity.WarpVelocity;
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
    private static final HashMap<String, Warp> warps = new HashMap<>();

    private static final Path WARPS_DIRECTORY =  WarpVelocity.getInstance().getDataFolder().resolve("warps");

    public static boolean warpExists(String warpName) {
        return warps.containsKey(normalise(warpName));
    }

    public static @Nullable Warp getWarp(String warpName) {
        String normalisedWarpName = normalise(warpName);

        if (warps.containsKey(normalisedWarpName)) {
            return warps.get(normalisedWarpName);
        }

        return null;
    }

    public static List<String> getAllWarpNames() {
        return warps.values().stream().map(Warp::getName).toList();
    }

    public static boolean addWarp(Warp newWarp) {
        try {
            putWarp(newWarp);
            saveWarp(newWarp);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void putWarp(Warp warp) throws Exception {
        String warpName = normalise(warp.getName());

        if (warps.containsKey(warpName)) {
            throw new Exception("Warp already exists");
        }
        warps.put(warpName, warp);
    }

    public static void saveWarp(Warp warp) {
        // TODO: If warp isPrivate then store under the creator's name - instead of server/world

        Path path = WARPS_DIRECTORY
            // server & world directories exist to make warps easier to manage manually
            .resolve(warp.getServer())
            .resolve(warp.getLocation().world())
            .resolve(normalise(warp.getName()) + ".yml");
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(path).build();

        try {
            Files.createDirectories(path.getParent());

            ConfigurationNode root = loader.load();
            root.set(Warp.class, warp);
            loader.save(root);
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    private static String normalise(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
