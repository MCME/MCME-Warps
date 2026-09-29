package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.WarpLoader;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Production {@link WarpFileIo}: reads and writes warp {@code .yml} files with Configurate.
 * (Configurate's {@code ConfigurateException} is an {@link IOException}, so it propagates cleanly.)
 */
public class ConfigurateWarpIo implements WarpFileIo {

    @Override
    public void write(Path path, Warp warp) throws IOException {
        Files.createDirectories(path.getParent());

        YamlConfigurationLoader loader = WarpLoader.build(path);
        ConfigurationNode root = loader.load();
        root.set(Warp.class, warp);
        loader.save(root);
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        return Files.deleteIfExists(path);
    }

    @Override
    public boolean exists(Path path) {
        return Files.exists(path);
    }
}
