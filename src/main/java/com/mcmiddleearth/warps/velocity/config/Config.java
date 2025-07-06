package com.mcmiddleearth.warps.velocity.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class Config {
    // Used by configurate to load & dump instances of this class to yaml (using reflection)
    public Config() {}

    private int privateWarpLimit = 25;

    public int getPrivateWarpLimit() {
        return privateWarpLimit;
    }
}
