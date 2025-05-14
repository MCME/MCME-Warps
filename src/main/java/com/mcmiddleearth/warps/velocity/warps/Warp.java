package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;

public class Warp {
    // TeleportData
    // Name
    // Server???

    // creator
    // invitations
    // permissions
    // title, subtitle, message?
    // region

    private final String name;
    private final String server;
    private final SimpleLocation location;

    public Warp(String name, String server, SimpleLocation location) {
        this.name = name;
        this.server = server;
        this.location = location;
    }

    public String getName() { return name; }
    public String getServer() { return server; }
    // TODO: Rename to ...?
    public SimpleLocation getLocation() { return location; }
}
