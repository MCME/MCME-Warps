package com.mcmiddleearth.warps.paper.listener;

import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.messageprotocols.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class MessageListener implements PluginMessageListener {

    private static final EnumSet<TeleportMessage.Subchannel> SAME_SERVER_TYPES = EnumSet.of(
        TeleportMessage.Subchannel.SAME_SERVER,
        TeleportMessage.Subchannel.LOCAL_WARP
    );

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
        final RequestLocationMessage.Result data;
        try {
            data = RequestLocationMessage.read(bytes);
        } catch (MalformedMessageException e) {
            rejectMalformed(player, "location-request", e);
            return;
        }
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
        final TeleportMessage.Result result;
        try {
            result = TeleportMessage.read(bytes);
        } catch (MalformedMessageException e) {
            rejectMalformed(player, "teleport", e);
            return;
        }
        String warpName = result.warpName();
        SimpleLocation data = result.data();

        boolean isLocalWarp = result.subchannel() == TeleportMessage.Subchannel.LOCAL_WARP;

        World world = isLocalWarp ? player.getWorld() : Bukkit.getWorld(data.world());
        if (world == null) {
            player.sendRichMessage("<red>Unable to perform the teleport, no world exists with name '%s'".formatted(data.world()));
            return;
        }

        Location teleportLocation = new Location(world, data.x(), data.y(), data.z(), data.yaw(), data.pitch());

        // Avoid redundant teleport and avoid a success message (which increments the warp counter)
        if (player.getLocation().equals(teleportLocation)) {
            // Only notify the player if the warp is on the same server as them
            // (since their previous position on the newly connected server could be the same as teleportLocation)
            if (SAME_SERVER_TYPES.contains(result.subchannel())) {
                    player.sendRichMessage("<gray>You are already at warp '%s'".formatted(warpName));
            }
            return;
        }

        player.teleportAsync(teleportLocation).thenAccept(success -> {
            if (success) {
                // Report a successful warp
                player.sendPluginMessage(
                    plugin,
                    Channels.WARP,
                    TeleportResult.serialise(warpName, TeleportResult.ResultType.SUCCESS)
                );
            } else {
                // Never leave the player with no feedback (UX): teleportAsync can decline
                // (chunk load failure, cancelled move, world unloaded).
                player.sendRichMessage("<red>Couldn't warp you there right now - please try again in a moment.");
            }
        });
    }

    private void handleMisc(Player player, byte[] bytes) {
        final MiscMessage.Result data;
        try {
            data = MiscMessage.read(bytes);
        } catch (MalformedMessageException e) {
            // No player-facing action here, so just log and drop.
            plugin.getComponentLogger().warn(Component.text(
                "Dropped a malformed misc message from the proxy: " + e.getMessage()));
            return;
        }

        if (data.subchannel().equals(MiscMessage.Subchannel.UPDATE_COMMANDS)) {
            player.updateCommands();
        }
    }

    /**
     * Root cause #2: a malformed message means the proxy and backend jars are skewed (or the payload
     * was corrupt). Log it and give the waiting player honest feedback instead of acting on bad data.
     */
    private void rejectMalformed(Player player, String kind, MalformedMessageException e) {
        plugin.getComponentLogger().warn(Component.text(
            "Dropped a malformed " + kind + " message from the proxy: " + e.getMessage()));
        player.sendRichMessage("<red>Your warp request failed - the server may be updating. Please try again shortly.");
    }
}