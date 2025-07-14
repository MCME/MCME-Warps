package com.mcmiddleearth.warps.paper.listener;

import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.messageprotocols.PlayerLocationMessage;
import com.mcmiddleearth.warps.core.messageprotocols.RequestLocationMessage;
import com.mcmiddleearth.warps.core.messageprotocols.TeleportMessage;
import com.mcmiddleearth.warps.core.messageprotocols.TeleportResult;
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
                var result = TeleportMessage.read(bytes);
                String warpName = result.warpName();
                SimpleLocation data = result.data();

                World world = Bukkit.getWorld(data.world());
                if (world == null) {
                    player.sendRichMessage("Unable to perform the teleport, no world exists with name " + data.world());
                    break;
                }
                Location warpLocation = new Location(world, data.x(), data.y(), data.z(), data.yaw(), data.pitch());

                // TODO: check terrain at target position and adapt if underground

                if (player.getLocation().equals(warpLocation)) {
                    // Avoid sending a message if the player swapped servers
                    // (their previous position on that server could be the same as warpLocation)
                    if (result.subchannel().equals(TeleportMessage.Subchannel.TELEPORT_SAME_SERVER)) {
                        player.sendRichMessage("<gray>You are already at warp " + warpName);
                    }
                    break;
                }

                player.teleportAsync(warpLocation).thenAccept(success -> {
                    if (success) {
                        player.sendPluginMessage(
                            plugin,
                            Channels.WARP,
                            TeleportResult.serialise(warpName, TeleportResult.ResultType.SUCCESS)
                        );
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
