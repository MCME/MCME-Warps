package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.*;
import com.mcmiddleearth.warps.paper.listener.MessageListener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPI;
import org.slf4j.event.Level;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class WarpPaper extends JavaPlugin {

    private static WarpPaper instance;

    private final Path WARPS_DIRECTORY = this.getDataPath().resolve("warps");
    private WarpWatcher watcher;
    private Boolean isDebugEnabled;

    public static WarpPaper getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        var listener = new MessageListener(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.WARP, listener);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.PLAYER_LOCATION, listener);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.MISC, listener);

        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.WARP);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.PLAYER_LOCATION);

        final Path dataPath = this.getDataPath();
        if (!Files.exists(dataPath)) {
            getComponentLogger().info("Creating the MCME-Warps data directory");
            try {
                Files.createDirectories(dataPath);
            } catch (IOException e) {
                getComponentLogger().error("Something went wrong whilst creating the data directory ({}) - {}", dataPath, e.getMessage());
            }
        }

        saveDefaultConfig();
        isDebugEnabled = getConfig().getBoolean("debug");

        Plugin dynmapPlugin = getServer().getPluginManager().getPlugin("dynmap");

        if (dynmapPlugin instanceof DynmapCommonAPI dynmap) {
            MapAPI mapAPI = new DynmapAPI(dynmap, loadLayers());
            loadMarkers(mapAPI);

            WarpWatcher watcher;
            watcher = new WarpWatcher(this, WARPS_DIRECTORY, mapAPI);
            watcher.start();
            this.watcher = watcher;
        } else {
            getComponentLogger().warn("Dynmap not found, skipping warp loading");
        }

    }

    @Override
    public void onDisable() {
        if (watcher != null) watcher.stop();
    }

    public void debug(Object message) {
        if (isDebugEnabled) {
            getComponentLogger().info("[DEBUG]: {}", message);
        }
    }

    private void loadMarkers(MapAPI mapAPI) {
        if (!Files.exists(WARPS_DIRECTORY)) {
            getComponentLogger().warn("The warps directory does not exist ({}), skipping warp loading", WARPS_DIRECTORY);
            return;
        }

        try (Stream<Path> pathStream = Files.walk(WARPS_DIRECTORY, FileVisitOption.FOLLOW_LINKS)) {
            List<Path> yamlFiles = pathStream
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".yml"))
                .toList();

            getComponentLogger().info("Loading {} warps from {}", yamlFiles.size(), WARPS_DIRECTORY);

            for (Path file : yamlFiles) {
                try {
                    ConfigurationNode root = WarpLoader.build(file).load();
                    BaseWarp baseWarp = root.get(BaseWarp.class);
                    mapAPI.addMarker(baseWarp);

                } catch (Exception e) {
                    getComponentLogger().error("Failed to load warp at {} - {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            getComponentLogger().error("Failed to walk the warps directory ({}) - {}", WARPS_DIRECTORY, e.getMessage());
        }
    }

    private List<Layer> loadLayers() {
        List<Layer> layers = new ArrayList<>();

        String defaultLabel = getConfig().getString("layers.default.label", "warps");
        int defaultMinZoom = getConfig().getInt("layers.default.min-zoom", -1);
        int defaultPriority = getConfig().getInt("layers.default.priority", 0);
        layers.add(new Layer("default", defaultLabel, defaultMinZoom, defaultPriority));

        ConfigurationSection section =
            getConfig().getConfigurationSection("layers.custom");
        if (section == null) return layers;

        for (String key : section.getKeys(false)) {
            ConfigurationSection layerSection = section.getConfigurationSection(key);

            String name = layerSection.getString("label", "UNKNOWN");
            int minZoom = layerSection.getInt("minZoom", -1);
            int priority = layerSection.getInt("priority", 0);

            layers.add(new Layer(key, name, minZoom, priority));
        }

        return layers;
    }
};