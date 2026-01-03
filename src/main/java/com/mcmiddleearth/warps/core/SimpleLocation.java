package com.mcmiddleearth.warps.core;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

@ConfigSerializable
public record SimpleLocation(
    @Required String world,
    @Required double x, @Required double y, @Required double z,
    @Required float yaw, @Required float pitch
) {}