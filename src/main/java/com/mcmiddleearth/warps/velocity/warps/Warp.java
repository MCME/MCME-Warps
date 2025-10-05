package com.mcmiddleearth.warps.velocity.warps;

import com.google.common.base.Strings;
import com.mcmiddleearth.warps.core.BaseWarp;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.WarpIcon;
import com.mcmiddleearth.warps.velocity.Permission;
import com.velocitypowered.api.proxy.Player;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.HashSet;
import java.util.Set;
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

    // If a @Required field is missing when loading a warp.yml file, configurate errors and doesn't load that warp
    @Required private UUID creator;
    @Required private String server;
    @Required private Warp.Type type;
    // Can't be final, otherwise Configurate can't set members on load
    private Set<UUID> members = new HashSet<>();
    private int visits = 0;
    private String welcomeMessage = null;

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
    public Set<UUID> getMembers() { return members; }

    public boolean isOfType(Warp.Type type) {
        return this.type.equals(type);
    }
    public boolean isCreator(Player player) {
        return creator.equals(player.getUniqueId());
    }

    public void setName(String name) { this.name = name; }
    public void setServer(String server) { this.server = server; }
    public void setLocation(SimpleLocation location) { this.location = location; }
    public void setIcon(WarpIcon icon) { this.icon = icon; }
    public void setType(Type type) { this.type = type; }

    public void addMember(UUID playerId) {
        this.members.add(playerId);
    }
    public void removeMember(UUID playerId) {
        this.members.remove(playerId);
    }

    public int getVisits() { return this.visits; }
    public void addVisit() {
        this.visits++;
    }
    public void setVisits(int visits) {
        this.visits = visits;
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
        if (player.hasPermission(Permission.OVERRIDE_USE.getNode())) return true;

        final String worldName = getLocation().world().toLowerCase();
        if (!player.hasPermission("mcmewarps.world-access." + worldName)) {
            return false;
        }

        if (isOfType(Type.PUBLIC)) {
            return true;
        }

        final boolean isMember = members.contains(player.getUniqueId());
        if (isMember) return true;

        return player.getUniqueId().equals(creator);
    }

    /**
     * Specifies which players can modify this warp
     */
    public boolean isModifiable(Player player) {
        if (player.hasPermission(Permission.OVERRIDE_MODIFY.getNode())) return true;

        if (isOfType(Type.PUBLIC)) {
            return false;
        }

        return player.getUniqueId().equals(creator);
    }

    @Override
    public String toString() {
        return "Warp [name=%s, type=%s, world=%s, server=%s]"
            .formatted(name, type, getLocation().world(), server);
    }
}
