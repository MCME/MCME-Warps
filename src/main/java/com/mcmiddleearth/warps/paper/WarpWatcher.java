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
    private final HashMap<WatchKey, Path> registeredDirectoryKeyToWorldPath = new HashMap<>();

    public WarpWatcher(JavaPlugin plugin, Path warpDir, MapAPI mapAPI) {
        this.plugin = plugin;
        this.warpDir = warpDir;
        this.mapAPI = mapAPI;
    }

    // NOTE: The modified file never has a MODIFY event on the MCME server
    // Instead .tmp files are created and modified before the modified file is re-created
    // Therefore the code doesn't listen for StandardWatchEventKinds.ENTRY_MODIFY
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
                                StandardWatchEventKinds.ENTRY_DELETE
                            );
                            registeredDirectoryKeyToWorldPath.put(key, worldPath);
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
                    // Safe to cast this because we are only watching for create/delete/modify
                    // This path is relative from the registered world directory
                    Path worldRelativePath = (Path) event.context();
                    // A path from the dataDirectory of the warp that was created/deleted/modified
                    Path warpPath = registeredDirectoryKeyToWorldPath.get(key).resolve(worldRelativePath);

                    WarpPaper.getInstance().debug(event.kind().name());
                    WarpPaper.getInstance().debug(worldRelativePath);
                    WarpPaper.getInstance().debug(warpPath);

                    // Ignore tmp files
                    if (!worldRelativePath.getFileName().toString().endsWith(".yml")) {
                        continue;
                    }

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            String warpName = com.google.common.io.Files.getNameWithoutExtension(warpPath.getFileName().toString());

                            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                                mapAPI.removeMarker(warpName);

                                ConfigurationNode root = WarpLoader.build(warpPath).load();
                                BaseWarp baseWarp = root.get(BaseWarp.class);
                                if (baseWarp == null) return;
                                mapAPI.addMarker(baseWarp);
                            }
                            else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                mapAPI.removeMarker(warpName);
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