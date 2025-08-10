package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.BaseWarp;
import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.SafeEnumSerializer;
import com.mcmiddleearth.warps.core.WarpTag;
import com.mcmiddleearth.warps.paper.listener.MessageListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPI;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public final class WarpPaper extends JavaPlugin {

    @Override
    public void onEnable() {
        var listener = new MessageListener(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.WARP, listener);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.PLAYER_LOCATION, listener);

        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.WARP);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.PLAYER_LOCATION);

        Plugin dynmapPlugin = getServer().getPluginManager().getPlugin("dynmap");

        if (dynmapPlugin instanceof DynmapCommonAPI dynmap) {
            MapAPI mapAPI = new DynmapAPI(dynmap);
            loadMarkers(mapAPI);
        } else {
            getComponentLogger().warn("Dynmap not found, skipping warp loading");
        }
    }

    @Override
    public void onDisable() {
    }

    private void loadMarkers(MapAPI mapAPI) {
        final Path WARPS_DIRECTORY =  Paths.get(this.getDataFolder().getPath()).resolve("warps");

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
                    YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                        .defaultOptions(opts -> opts.serializers(build ->
                            build.register(
                                WarpTag.class,
                                new SafeEnumSerializer<>(WarpTag.class, WarpTag.DEFAULT))
                            )
                        )
                        .path(file)
                        .build();

                    ConfigurationNode root = loader.load();
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
};