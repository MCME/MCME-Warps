package com.mcmiddleearth.warps.velocity.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

@ConfigSerializable
public class Config {
    // Used by configurate to load & dump instances of this class to yaml (using reflection)
    public Config() {}

    @Required private int privateWarpLimit;

    public int getPrivateWarpLimit() {
        return privateWarpLimit;
    }
}
