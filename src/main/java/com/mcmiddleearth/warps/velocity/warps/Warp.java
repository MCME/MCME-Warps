package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.TeleportMessage;
import com.mcmiddleearth.warps.core.WarpLocation;

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
    private final WarpLocation location;

    public Warp(String name, String server, WarpLocation location) {
        this.name = name;
        this.server = server;
        this.location = location;
    }

    public String getName() { return name; }
    public String getServer() { return server; }
    // TODO: Rename to ...?
    public WarpLocation getLocation() { return location; }
}
