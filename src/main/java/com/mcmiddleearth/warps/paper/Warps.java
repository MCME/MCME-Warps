package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.TeleportMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class Warps extends JavaPlugin implements PluginMessageListener {

    @Override
    public void onEnable() {
//        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.MAIN);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.MAIN, this);
    }

    @Override
    public void onDisable() {
    }

    // TODO: Move to a listener class
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] bytes) {
        if(!channel.equals(Channels.MAIN)) return;

        TeleportMessage.TeleportData data = null;
        try {
            data = TeleportMessage.read(bytes);
        } catch (IOException e) {
            // Q: Throw or just log???
            throw new RuntimeException(e);
        }

        // Q: Is handling nulls needed, or can they be trusted to always come through?
        World world = Bukkit.getWorld(data.world());
        Location location = new Location(world, data.x(), data.y(), data.z(), data.yaw(), data.pitch());

        // TODO: check terrain at target position and adapt if underground

        player.teleportAsync(location).thenAccept(success -> {
            if (success) {
                // Q: Title/subtitle?
                // TODO: Notify player if teleport location was underground
                player.sendMessage("teleport complete");
            }
        });
    }
};