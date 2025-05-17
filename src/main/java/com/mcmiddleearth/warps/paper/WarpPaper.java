package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.*;
import com.mcmiddleearth.warps.core.PlayerLocationMessage;
import com.mcmiddleearth.warps.core.RequestLocationMessage;
import com.mcmiddleearth.warps.core.TeleportMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public final class WarpPaper extends JavaPlugin implements PluginMessageListener {

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.WARP, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.CREATE_WARP, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.CREATE_WARP);
    }

    @Override
    public void onDisable() {
    }

    // TODO: Move to a listener class
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] bytes) {

        switch (channel) {
            case Channels.WARP: {
                SimpleLocation data = TeleportMessage.read(bytes).data();

                // Q: Is handling nulls needed, or can they be trusted to always come through?
                World world = Bukkit.getWorld(data.world());
                Location location = new Location(world, data.x(), data.y(), data.z(), data.yaw(), data.pitch());

                // TODO: check terrain at target position and adapt if underground

                // FIXME: Currently causes player moved too quickly, use a scheduler to teleport on next tick
                player.teleportAsync(location).thenAccept(success -> {
                    if (success) {
                        // Q: Title/subtitle?
                        // TODO: Notify player if teleport location was underground

                        player.sendMessage("teleport complete");
                    }
                });
                break;
            }

            case Channels.CREATE_WARP:  {
                RequestLocationMessage.Result data = RequestLocationMessage.read(bytes);
                SimpleLocation location = new SimpleLocation(player.getWorld().getName(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());

                player.sendPluginMessage(
                    this,
                    Channels.CREATE_WARP,
                    PlayerLocationMessage.serialise(data.subchannel(), location, data.warpName())
                );
                break;
            }

            default:
                return;
        }
    }
};