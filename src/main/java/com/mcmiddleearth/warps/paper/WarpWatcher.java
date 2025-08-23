package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.BaseWarp;
import com.mcmiddleearth.warps.core.WarpLoader;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.nio.file.*;

public class WarpWatcher {
    private final JavaPlugin plugin;
    private final Path warpDir;
    private final MapAPI mapAPI;

    private WatchService watchService;
    private Thread watcherThread;

    public WarpWatcher(JavaPlugin plugin, Path warpDir, MapAPI mapAPI) {
        this.plugin = plugin;
        this.warpDir = warpDir;
        this.mapAPI = mapAPI;
    }

    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();

            try (var stream = Files.list(warpDir)) {
                stream
                    .filter(Files::isDirectory)
                    .forEach(warpFolder -> {
                        try {
                            warpFolder.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                // FIXME: When the proxy shutdowns this picks that every file was saved?!
                                StandardWatchEventKinds.ENTRY_MODIFY
                            );
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
                    Path changed = warpDir.resolve((Path) event.context());

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

                            // TODO: Modify -> remove and add???
                        } catch (Exception e) {
                            // FIXME:
                            System.out.println("Failed to handle event");
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