package com.mcmiddleearth.warps.core;

import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;

public class WarpLoader {
    private WarpLoader() {}

    public static YamlConfigurationLoader build(Path file) {
        return YamlConfigurationLoader.builder()
            .defaultOptions(WarpLoader::applyDefaultOptions)
            .path(file)
            .build();
    }

    private static ConfigurationOptions applyDefaultOptions(ConfigurationOptions options) {
        return options.serializers(build -> build.register(
            WarpTag.class,
            new SafeEnumSerializer<>(WarpTag.class, WarpTag.DEFAULT)
        ));
    }
}
