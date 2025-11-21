package com.mcmiddleearth.warps.paper.listener;

import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.messageprotocols.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class MessageListener implements PluginMessageListener {

    private static final int MAX_RADIUS = 15;
    private final Plugin plugin;

    public MessageListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] bytes) {
        switch (channel) {
            case Channels.WARP -> handleWarpRequest(player, bytes);
            case Channels.PLAYER_LOCATION -> handleLocationRequest(player, bytes);
            case Channels.MISC -> handleMisc(player, bytes);
            default -> plugin.getComponentLogger().warn(Component.text("Channel '" + channel + "' has no handler!"));
        }
    }

    private void handleLocationRequest(Player player, byte[] bytes) {
        RequestLocationMessage.Result data = RequestLocationMessage.read(bytes);
        SimpleLocation location = new SimpleLocation(
            player.getWorld().getName(),
            player.getX(), player.getY(), player.getZ(),
            player.getYaw(), player.getPitch()
        );

        player.sendPluginMessage(
            plugin,
            Channels.PLAYER_LOCATION,
            PlayerLocationMessage.serialise(data.subchannel(), location, data.warpName())
        );
    }

    private void handleWarpRequest(Player player, byte[] bytes) {
        var result = TeleportMessage.read(bytes);
        String warpName = result.warpName();
        SimpleLocation data = result.data();

        World world = Bukkit.getWorld(data.world());
        if (world == null) {
            player.sendRichMessage("<red>Unable to perform the teleport, no world exists with name " + data.world());
            return;
        }

        Location warpLocation = new Location(world, data.x(), data.y(), data.z(), data.yaw(), data.pitch());

        // Avoid redundant teleport and avoid a success message (which increments the warp counter)
        if (player.getLocation().equals(warpLocation)) {
            // Only notify the player if the warp is on the same server as them
            // (since their previous position on the newly connected server could be the same as warpLocation)
            if (result.subchannel().equals(TeleportMessage.Subchannel.TELEPORT_SAME_SERVER)) {
                player.sendRichMessage("<gray>You are already at warp " + warpName);
            }
            return;
        }

        player.teleportAsync(warpLocation).thenAccept(success -> {
            if (success) {
                // Report a successful warp
                player.sendPluginMessage(
                    plugin,
                    Channels.WARP,
                    TeleportResult.serialise(warpName, TeleportResult.ResultType.SUCCESS)
                );
            }
        });
    }

    private void handleMisc(Player player, byte[] bytes) {
        MiscMessage.Result data = MiscMessage.read(bytes);

        if (data.subchannel().equals(MiscMessage.Subchannel.UPDATE_COMMANDS)) {
            player.updateCommands();
        }
    }
}