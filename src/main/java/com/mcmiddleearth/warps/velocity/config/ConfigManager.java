package com.mcmiddleearth.warps.velocity.config;

import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.velocitypowered.api.proxy.Player;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final String DEFAULT_CONFIG_NAME = "default-config.yml";
    private static final Path CONFIG_FILE_PATH =  WarpVelocity.getDataFolder().resolve("config.yml");

    public static Config config;
    private static List<Map.Entry<String, Integer>> sortedWarpLimits;

    public static void loadConfig() {
        if (!Files.exists(CONFIG_FILE_PATH)) {
            WarpVelocity.getLogger().warn("No config.yml file found at {}, using the default config!", CONFIG_FILE_PATH);

            // Save the default-config
            try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_NAME)) {
                if (in == null) {
                    WarpVelocity.getLogger().error(DEFAULT_CONFIG_NAME + " not found inside the plugin resources - unable to load config");
                    throw new RuntimeException();
                }
                Files.createDirectories(CONFIG_FILE_PATH.getParent());
                Files.copy(in, CONFIG_FILE_PATH);
                WarpVelocity.getLogger().warn("Successfully copied the default config {}", CONFIG_FILE_PATH);
            } catch (IOException e) {
                WarpVelocity.getLogger().error("Failed to copy the default config to the data directory - {}", e.getMessage());
                throw new RuntimeException();
            }
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
            .path(CONFIG_FILE_PATH)
            .build();

        try {
            ConfigurationNode root = loader.load();
            config = root.get(Config.class);

            // Cache the sorted limits, largest firsts
            sortedWarpLimits = config.privateWarpLimits().configured().entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .toList();
        } catch (ConfigurateException e) {
            WarpVelocity.getLogger().error("Failed to load the config from {}, with error {}", CONFIG_FILE_PATH, e.getMessage());
            throw new RuntimeException();
        }
    }

    public static Config getConfig() {
        return config;
    }

    public record WarpLimit(String name, int limit) {}
    public static WarpLimit resolvePrivateWarpLimit(Player sender) {
        var defaultLimit = ConfigManager.getConfig().privateWarpLimits().defaultLimit();

        for (var entry : sortedWarpLimits) {
            if (sender.hasPermission("mcmewarps.limits." + entry.getKey())) {
                return new WarpLimit(entry.getKey(), entry.getValue());
            }
        }

        return new WarpLimit("default", defaultLimit);
    }
}