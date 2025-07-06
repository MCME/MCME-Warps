package com.mcmiddleearth.warps.velocity.config;

import com.mcmiddleearth.warps.velocity.WarpVelocity;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final String DEFAULT_CONFIG_NAME = "default-config.yml";
    private static final Path CONFIG_FILE_PATH =  WarpVelocity.getInstance().getDataFolder().resolve("config.yml");

    public static Config config;

    public static void loadConfig() {
        if (!Files.exists(CONFIG_FILE_PATH)) {
            WarpVelocity.getInstance().getLogger().warn("No config.yml file found at {}, using the default config!", CONFIG_FILE_PATH);

            // Save the default-config
            try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_NAME)) {
                if (in == null) {
                    WarpVelocity.getInstance().getLogger().error(DEFAULT_CONFIG_NAME + " not found inside the plugin resources - unable to load config");
                    throw new RuntimeException();
                }
                Files.createDirectories(CONFIG_FILE_PATH.getParent());
                Files.copy(in, CONFIG_FILE_PATH);
                WarpVelocity.getInstance().getLogger().warn("Successfully copied the default config {}", CONFIG_FILE_PATH);
            } catch (IOException e) {
                System.err.println("[ConfigManager] Failed to copy default config:");
                WarpVelocity.getInstance().getLogger().error("Failed to copy the default config to the data directory - {}", e.getMessage());
                throw new RuntimeException();
            }
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
            .path(CONFIG_FILE_PATH)
            .build();

        try {
            ConfigurationNode root = loader.load();
            config = root.get(Config.class);
        } catch (ConfigurateException e) {
            WarpVelocity.getInstance().getLogger().error("Failed to load the config from {}, with error {}", CONFIG_FILE_PATH, e.getMessage());
            throw new RuntimeException();
        }
    }

    public static Config getConfig() {
        return config;
    }
}