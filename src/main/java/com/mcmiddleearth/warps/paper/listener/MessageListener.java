package com.mcmiddleearth.warps.paper.listener;

import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.messageprotocols.PlayerLocationMessage;
import com.mcmiddleearth.warps.core.messageprotocols.RequestLocationMessage;
import com.mcmiddleearth.warps.core.messageprotocols.TeleportMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class MessageListener implements PluginMessageListener {

    final Plugin plugin;

    public MessageListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] bytes) {
        switch (channel) {
            case Channels.WARP: {
                SimpleLocation data = TeleportMessage.read(bytes).data();

                // Q: Is handling nulls needed, or can they be trusted to always come through?
                World world = Bukkit.getWorld(data.world());
                Location location = new Location(world, data.x(), data.y(), data.z(), data.yaw(), data.pitch());

                // TODO: check terrain at target position and adapt if underground

                // Causes player 'moved too quickly' warnings
                // Could fix with a scheduler to teleport on next tick, but it doesn't seem to be an issue
                player.teleportAsync(location).thenAccept(success -> {
                    if (success) {
                        // player.sendMessage("teleport complete");
                        // TODO: Title, subtitle & message
                        // TODO: Notify player if teleport location was underground
                    }
                });
                break;
            }

            case Channels.PLAYER_LOCATION:  {
                RequestLocationMessage.Result data = RequestLocationMessage.read(bytes);
                SimpleLocation location = new SimpleLocation(player.getWorld().getName(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());

                player.sendPluginMessage(
                    plugin,
                    Channels.PLAYER_LOCATION,
                    PlayerLocationMessage.serialise(data.subchannel(), location, data.warpName())
                );
                break;
            }

            default:
                plugin.getComponentLogger().warn(Component.text("Channel '" + channel + "' has no handler!"));
        }
    }
}
