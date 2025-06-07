package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;
import com.velocitypowered.api.proxy.Player;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

// https://docs.spongepowered.org/stable/en/plugin/configuration/serialization.html#using-objectmappers
// https://github.com/SpongePowered/Configurate/wiki/Object-Mapper

@ConfigSerializable
public class Warp {
    // Used by configurate to load & dump instances of this class (Warp) to yaml (using reflection)
    public Warp() {}

    /** The warp type - private or public */
    public enum Type {
        PRIVATE,
        PUBLIC
    }

    // TODO:
    // permissions
    // title, subtitle, message?
    // region? -> Only for main & moria?
    // player warp counter

    // If a @Required field is missing when loading a warp.yml file, configurate errors and doesn't load that warp
    @Required private UUID creator;
    @Required private String name;
    @Required private String server;
    @Required private SimpleLocation location;
    @Required private Warp.Type type;
    // Fields must be non-final to be modified
    private HashMap<UUID, String> members = new HashMap<>();

    public Warp(UUID creator, String name, String server, SimpleLocation location, Warp.Type type) {
        this.creator = creator;
        this.name = name;
        this.server = server;
        this.location = location;
        this.type = type;
    }

    public String getName() { return name; }
    public String getServer() { return server; }
    public SimpleLocation getLocation() { return location; }
    public UUID getCreator() { return creator; }
    public HashMap<UUID, String> getMembers() { return members; }

    public boolean isOfType(Warp.Type type) {
        return this.type.equals(type);
    }

    public void setName(String name) { this.name = name; }
    public void setServer(String server) { this.server = server; }
    public void setLocation(SimpleLocation location) { this.location = location; }

    public void addPlayer(Player player) {
        this.members.put(player.getUniqueId(), player.getUsername());
    }
    public void removePlayer(Player player) {
        this.members.remove(player.getUniqueId());
    }

    /**
     * A filter to limit what players can view and use this warp
     */
    public boolean isUsable(Player player) {
        if (isOfType(Type.PUBLIC)) {
            // TODO: Check player meets the perms (if any)
            return true;
        }

        // Q: What about staff/moderators?
        if (members.containsKey(player.getUniqueId())) return true;
        return player.getUniqueId().equals(creator);
    }

    /**
     * A filter to limit what players can view and modify this warp
     */
    public boolean isModifiable(Player player) {
        if (isOfType(Type.PUBLIC)) {
            // TODO: Player must have staff/moderator perms
            return true;
        }

        // Q: What about staff/moderators?
        return player.getUniqueId().equals(creator);
    }

//    public boolean isModifiableBy(Player player) {
//        return player.getUniqueId().equals(owner) || player.hasPermission("warp.admin");
//    }
//    public boolean isUsableBy(Player player) {
//        return isPublic || isModifiableBy(player);
//    }
}
