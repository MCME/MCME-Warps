package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

@ConfigSerializable
public class Warp {
    // Used by configurate to load & dump instances of this class (Warps) to yaml
    public Warp() {}

    // private/public
    // creator
    // invitations
    // permissions
    // title, subtitle, message?
    // region? -> Only for main & moria?
    // popularity count

    // @Required, causes an error to be thrown if configurate tries to load a warp.yml file without that field
    @Required private String name;
    @Required private String server;
    @Required private SimpleLocation location;

    public Warp(String name, String server, SimpleLocation location) {
        this.name = name;
        this.server = server;
        this.location = location;
    }

    public String getName() { return name; }
    public String getServer() { return server; }
    public SimpleLocation getLocation() { return location; }
}
