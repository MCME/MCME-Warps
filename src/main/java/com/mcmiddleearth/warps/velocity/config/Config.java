package com.mcmiddleearth.warps.velocity.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.List;
import java.util.Map;

@ConfigSerializable
public record Config(
    @Required int warpNameMaxLength,
    @Required Limits privateWarpLimits,
    Sql sql,
    List<String> layerKeys
) {
    @ConfigSerializable
    public record Limits(@Required Integer defaultLimit, @Required  Map<String, Integer> configured) {}

    @ConfigSerializable
    public record Sql(@Required String user, @Required String password, @Required String dbName, @Required String ip, @Required Integer port) {}
}