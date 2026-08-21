package com.mcmiddleearth.warps.paper;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the dynmap layer definitions out of the paper config.
 * <p>
 * Kept separate from {@link WarpPaper} so it can be unit tested against a plain
 * {@code YamlConfiguration} without a running server.
 */
public final class LayerParser {

    private LayerParser() {}

    /**
     * @param config the plugin's root configuration section
     * @return the default layer followed by every configured custom layer
     */
    public static List<Layer> parse(ConfigurationSection config) {
        List<Layer> layers = new ArrayList<>();

        String defaultLabel = config.getString("layers.default.label", "warps");
        int defaultMinZoom = config.getInt("layers.default.min-zoom", -1);
        int defaultPriority = config.getInt("layers.default.priority", 0);
        layers.add(new Layer("default", defaultLabel, defaultMinZoom, defaultPriority));

        ConfigurationSection section = config.getConfigurationSection("layers.custom");
        if (section == null) return layers;

        for (String key : section.getKeys(false)) {
            ConfigurationSection layerSection = section.getConfigurationSection(key);
            if (layerSection == null) continue;

            String name = layerSection.getString("label", "UNKNOWN");
            int minZoom = layerSection.getInt("min-zoom", -1);
            int priority = layerSection.getInt("priority", 0);

            layers.add(new Layer(key, name, minZoom, priority));
        }

        return layers;
    }
}
