package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.BaseWarp;
import com.mcmiddleearth.warps.core.WarpLoader;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;

public class WarpWatcher {
    private final JavaPlugin plugin;
    private final Path warpDir;
    private final MapAPI mapAPI;

    private WatchService watchService;
    private Thread watcherThread;
    private HashMap<WatchKey, Path> keyToPath = new HashMap<>();

    public WarpWatcher(JavaPlugin plugin, Path warpDir, MapAPI mapAPI) {
        this.plugin = plugin;
        this.warpDir = warpDir;
        this.mapAPI = mapAPI;
    }

    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();

            // Register each (world) folder within warpDir
            try (var stream = Files.list(warpDir)) {
                stream
                    .filter(Files::isDirectory)
                    .forEach(worldPath -> {
                        try {
                            var key = worldPath.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                StandardWatchEventKinds.ENTRY_MODIFY
                            );
                            keyToPath.put(key, worldPath);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to register watch service: " + e.getMessage());
            return;
        }

        watcherThread = new Thread(this::processEvents, "WarpWatcherThread");
        watcherThread.start();
    }

    private void processEvents() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take(); // blocks until event
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // Resolve relative path to absolute
                    Path changed = keyToPath.get(key).resolve((Path) event.context());

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            if (kind.name().equals(StandardWatchEventKinds.ENTRY_CREATE.name())) {
                                ConfigurationNode root = WarpLoader.build(changed).load();
                                BaseWarp baseWarp = root.get(BaseWarp.class);
                                if (baseWarp == null) return;
                                mapAPI.addMarker(baseWarp);
                            }
                            else if (kind.name().equals(StandardWatchEventKinds.ENTRY_DELETE.name())) {
                                String warpName = com.google.common.io.Files.getNameWithoutExtension(changed.getFileName().toString());
                                mapAPI.removeMarker(warpName);
                            }
                            else if (kind.name().equals(StandardWatchEventKinds.ENTRY_MODIFY.name())) {
                                String warpName = com.google.common.io.Files.getNameWithoutExtension(changed.getFileName().toString());
                                mapAPI.removeMarker(warpName);

                                ConfigurationNode root = WarpLoader.build(changed).load();
                                BaseWarp baseWarp = root.get(BaseWarp.class);
                                if (baseWarp == null) return;
                                mapAPI.addMarker(baseWarp);
                            }
                        } catch (ConfigurateException e) {
                            plugin.getComponentLogger().error(Component.text("Failed to handle warp marker update - " + e.getMessage()));
                        }
                    });
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop() {
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}

        if (watcherThread != null) watcherThread.interrupt();
    }
}