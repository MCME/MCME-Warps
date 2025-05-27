package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;
import com.velocitypowered.api.proxy.Player;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

@ConfigSerializable
public class Warp {
    // Used by configurate to load & dump instances of this class (Warp) to yaml
    public Warp() {}

    // private/public
    // creator
    // invitations
    // permissions
    // title, subtitle, message?
    // region? -> Only for main & moria?
    // popularity count

    // If a @Required field is missing when loading a warp.yml file, configurate errors and doesn't load that warp
    @Required private String name;
    @Required private String server;
    @Required private SimpleLocation location;
    private boolean isPublic;
//    @Required private boolean isPublic;

//    enum Type {
//        /**
//         * A private Warp.
//         */
//        PRIVATE, /**
//         * A public Warp.
//         */
//        PUBLIC
//    }

    public Warp(String name, String server, SimpleLocation location) {
        this.name = name;
        this.server = server;
        this.location = location;
    }

    public String getName() { return name; }
    public String getServer() { return server; }
    public SimpleLocation getLocation() { return location; }

    public boolean isUsable(Player player) {
//        if (isPublic) {
//            // if no perms return true
//            // if player meets perms return true
//            return false;
//        }

        // Private warp
        // if moderator return true??? (user has mod perms)
        // if creator return true
//        return false;

        return true;
    }

    public boolean isModifiable(Player player) {
//        if (isPublic) {
//            // if public then only staff
//        }

        // if private then only creator
//        return false;

        return true;
    }

//    public boolean isModifiableBy(Player player) {
//        return player.getUniqueId().equals(owner) || player.hasPermission("warp.admin");
//    }
//    public boolean isUsableBy(Player player) {
//        return isPublic || isModifiableBy(player);
//    }
}
