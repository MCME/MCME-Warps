package com.mcmiddleearth.warps.velocity.warps;

import com.google.common.base.Strings;
import com.mcmiddleearth.warps.core.BaseWarp;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.WarpTag;
import com.mcmiddleearth.warps.velocity.Permission;
import com.velocitypowered.api.proxy.Player;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.HashMap;
import java.util.UUID;

// https://docs.spongepowered.org/stable/en/plugin/configuration/serialization.html#using-objectmappers
// https://github.com/SpongePowered/Configurate/wiki/Object-Mapper

@ConfigSerializable
public class Warp extends BaseWarp {
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
    @Required private String server;
    @Required private Warp.Type type;
    // Can't be final, otherwise Configurate can't set members on load
    private HashMap<UUID, String> members = new HashMap<>();
    private int visits = 0;
    private String welcomeMessage;

    public Warp(UUID creator, String name, String server, SimpleLocation location, Warp.Type type) {
        this.creator = creator;
        this.name = name;
        this.server = server;
        this.location = location;
        this.type = type;
    }

    // Copy Constructor
    public Warp(Warp otherWarp) {
        this.creator = otherWarp.creator;
        this.name = otherWarp.name;
        this.server = otherWarp.server;
        this.location = otherWarp.location;
        this.type = otherWarp.type;
        this.members = otherWarp.members;
        this.visits = otherWarp.visits;
        this.welcomeMessage = otherWarp.welcomeMessage;
    }

    public String getServer() { return server; }
    public UUID getCreator() { return creator; }
    public HashMap<UUID, String> getMembers() { return members; }

    public boolean isOfType(Warp.Type type) {
        return this.type.equals(type);
    }
    public boolean isCreator(Player player) {
        return creator.equals(player.getUniqueId());
    }

    public void setName(String name) { this.name = name; }
    public void setServer(String server) { this.server = server; }
    public void setLocation(SimpleLocation location) { this.location = location; }
    public void setTag(WarpTag tag) { this.tag = tag; }
    public void setType(Type type) { this.type = type; }

    public void addPlayer(Player player) {
        this.members.put(player.getUniqueId(), player.getUsername());
    }
    public void removePlayer(Player player) {
        this.members.remove(player.getUniqueId());
    }

    public int getVisits() { return this.visits; }
    public void addVisit() {
        this.visits++;
    }

    public String getWelcomeMessage() {
        if (Strings.isNullOrEmpty(welcomeMessage)) {
            return "<blue>Welcome to " + getName();
        }

        return welcomeMessage;
    }
    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    /**
     * Specifies which players can use this warp
     */
    public boolean isUsable(Player player) {
        if (isOfType(Type.PUBLIC)) {
            // TODO: Check player meets the perms (if any)

            return player.hasPermission("mcmewarps.world-access." + getLocation().world().toLowerCase());
        }

        // Q: Should staff/moderators be able to see & use other people's private warps?

        if (members.containsKey(player.getUniqueId())) return true;
        return player.getUniqueId().equals(creator);
    }

    /**
     * Specifies which players can modify this warp
     */
    public boolean isModifiable(Player player) {
        if (isOfType(Type.PUBLIC)) {
            return player.hasPermission(Permission.EDIT_PUBLIC_WARPS.getNode());
        }

        // Q: Should staff/moderators be able to see & use other people's private warps?

        return player.getUniqueId().equals(creator);
    }
}
