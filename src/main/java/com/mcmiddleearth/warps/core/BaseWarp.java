package com.mcmiddleearth.warps.core;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

// https://docs.spongepowered.org/stable/en/plugin/configuration/serialization.html#using-objectmappers
// https://github.com/SpongePowered/Configurate/wiki/Object-Mapper

/**
 * A minimal Warp class, providing getters for fields needed to render a Warp on a map
 */
@ConfigSerializable
public class BaseWarp {
    // Used by configurate to load & dump instances of this class (Warp) to yaml (using reflection)
    public BaseWarp() {}

    // If a @Required field is missing when loading a warp.yml file, configurate errors and doesn't load that warp
    @Required protected String name;
    @Required protected SimpleLocation location;
    protected WarpIcon icon = WarpIcon.DEFAULT;

    public String getName() { return name; }
    public SimpleLocation getLocation() { return location; }
    public WarpIcon getIcon() { return icon; }
}