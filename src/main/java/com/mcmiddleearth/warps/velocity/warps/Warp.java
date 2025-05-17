package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class Warp {
    // Used by configurate to load & dump Warps to yaml
    public Warp() {}

    // creator
    // invitations
    // permissions
    // title, subtitle, message?
    // region
    // popularity count

    private String name;
    private String server;
    private SimpleLocation location;

    public Warp(String name, String server, SimpleLocation location) {
        this.name = name;
        this.server = server;
        this.location = location;
    }

    public String getName() { return name; }
    public String getServer() { return server; }
    // TODO: Rename to ...?
    public SimpleLocation getLocation() { return location; }

    public void setServer(String name) {this.server = name;}
    public void setLocation(SimpleLocation location) {this.location = location;}
    public void setName(String name) {this.name = name;}
}
