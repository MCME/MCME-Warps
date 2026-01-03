package com.mcmiddleearth.warps.velocity.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

@ConfigSerializable
public record Config(
    @Required int privateWarpLimit,
    @Required int warpNameMaxLength,
    Sql sql
) {
    @ConfigSerializable
    public record Sql(String user, String password, String dbName, String ip, Integer port) {}
}