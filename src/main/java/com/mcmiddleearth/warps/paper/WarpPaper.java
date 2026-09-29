package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.*;
import com.mcmiddleearth.warps.paper.listener.MessageListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPI;
import org.slf4j.event.Level;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.nio.file.*;
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

        MapAPI mapAPI = createMapAPI();

        if (mapAPI != null) {
            loadMarkers(mapAPI);

            WarpWatcher watcher;
            watcher = new WarpWatcher(this, WARPS_DIRECTORY, mapAPI);
            watcher.start();
            this.watcher = watcher;
        }

    }

    /**
     * Builds the marker publisher for whichever map plugin is available, or returns
     * {@code null} when there is none - in which case this plugin still enables and simply
     * publishes no markers.
     * <p>
     * The check is {@code isPluginEnabled}, not {@code getPlugin() != null}. Dynmap has no
     * 26.x build, so it is present on disk, gets loaded, fails to enable, and is left with a
     * null internal core. Its class still implements {@link DynmapCommonAPI}, so an
     * {@code instanceof} test passes and the first API call NPEs inside Dynmap - which is
     * what previously killed {@code onEnable} and got this whole plugin disabled.
     *
     * @return a usable {@link MapAPI}, or {@code null} if markers cannot be published
     */
    private MapAPI createMapAPI() {
        if (!getServer().getPluginManager().isPluginEnabled("dynmap")) {
            getComponentLogger().warn("Dynmap is not enabled - warps will load but no map markers will be published");
            return null;
        }

        Plugin dynmapPlugin = getServer().getPluginManager().getPlugin("dynmap");
        if (!(dynmapPlugin instanceof DynmapCommonAPI dynmap)) {
            getComponentLogger().warn("The installed dynmap plugin does not expose DynmapCommonAPI - no map markers will be published");
            return null;
        }

        try {
            return new DynmapAPI(dynmap, loadLayers());
        } catch (Throwable ex) {
            // Defence in depth: an enabled-but-incompatible Dynmap must not take this
            // plugin down with it. Throwable, not Exception - an API that moved between
            // major versions surfaces as NoClassDefFoundError / NoSuchMethodError.
            getComponentLogger().warn(
                "Dynmap is enabled but its marker API could not be used ({}: {}) - no map markers will be published",
                ex.getClass().getSimpleName(), ex.getMessage());
            return null;
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
        return LayerParser.parse(getConfig());
    }
};