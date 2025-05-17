package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.velocity.WarpVelocity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class WarpManager {
    private static final HashMap<String, Warp> warps = new HashMap<>();

    // FIXME: Feels risky, but getInstance seems to already be populated?
    private static final Path WARPS_DIRECTORY =  WarpVelocity.getInstance().getDataFolder().resolve("warps");

    // Q: Don't use static? Instantiate with dataFolder instead and re-use that 1 instance?
    public WarpManager() {
//        this.file = new File(dataFolder, "warps.yml");
//        this.config = YamlConfiguration.loadConfiguration(file);
//        loadWarps();
    }

    public static @Nullable Warp getWarp(String warpName) {
        if (warps.containsKey(warpName)) {
            return warps.get(warpName);
        }

        return null;
    }

    public static void saveWarp(Warp warp) {
        // Q: Good idea to store warp names lowercase?
//        warps.put(warp.getName().toLowerCase(), warp);
        // FIXME: Prevent dupes
        warps.put(warp.getName(), warp);

        Path path = WARPS_DIRECTORY
            // Server & World directories exist to make warps easier to manage manually
            .resolve(warp.getServer())
            .resolve(warp.getLocation().world())
            .resolve(warp.getName() + ".yml");
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

//    public boolean warpExists(String name) {
//        return warps.containsKey(name.toLowerCase());
//    }

//    public static Collection<Warp> getAllWarps() {
//        return warps.values();
//    }

    public static Set<String> getAllWarpNames() {
//        return new ArrayList<String>(warps.keySet());
        return warps.keySet();
    }


    public static Warp loadWarp(Path filePath) {
        try {
//            if (!Files.exists(filePath)) return null;

            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(filePath)
                .build();

            ConfigurationNode root = loader.load();
            return root.get(Warp.class);
        } catch (IOException e) {
            // Q: Any point in this? Already catching in loadAllWarps
            throw new RuntimeException("Failed to load warp from: " + filePath, e);
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
//                    if (warp != null) {
                        warps.put(warp.getName(), warp);
//                    }
                } catch (Exception e) {
                    WarpVelocity.getInstance().getLogger().error("Failed to load warp at {} - {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan warp directory: " + WARPS_DIRECTORY, e);
        }
    }
}
