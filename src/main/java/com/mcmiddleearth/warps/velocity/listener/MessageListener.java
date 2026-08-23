package com.mcmiddleearth.warps.velocity.listener;

import com.mcmiddleearth.warps.core.messageprotocols.*;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.Permission;
import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpPredicates;
import com.mcmiddleearth.warps.velocity.config.Config;
import com.mcmiddleearth.warps.velocity.config.ConfigManager;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Instant;

public class MessageListener {
    @Subscribe
    public void onPluginMessageFromBackend(PluginMessageEvent event) {
        final ChannelIdentifier eventChannel = event.getIdentifier();

        // Only concern ourselves with the warp plugin channels.
        boolean isWarpChannel = eventChannel.equals(ChannelIdentifiers.MAIN_ID)
            || eventChannel.equals(ChannelIdentifiers.PLAYER_LOCATION_CHANNEL_ID)
            || eventChannel.equals(ChannelIdentifiers.MISC_ID);
        if (!isWarpChannel) {
            return;
        }

        // These channels are strictly proxy<->backend. Consume EVERY message on them, unconditionally
        // and BEFORE the source check, so a client-sent message is never forwarded on to a backend -
        // Velocity forwards any message a listener leaves un-handled. Without this a modified client
        // could impersonate the proxy to the backend and forge teleports / warp creates / moves
        // (SEC-2, and the client-reachable half of SEC-1/SEC-3). Setting it before reading the payload
        // also prevents a malformed message from being leaked onward if parsing throws.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        // Only a backend server may drive these; a client-sourced message is now dropped.
        if (!(event.getSource() instanceof ServerConnection backend)) {
            return;
        }

        Player player = backend.getPlayer();

        if (eventChannel.equals(ChannelIdentifiers.MAIN_ID)) {
            final TeleportResult.Response response;
            try {
                response = TeleportResult.read(event.getData());
            } catch (MalformedMessageException e) {
                // Root cause #2: reject a skewed/corrupt message loudly instead of acting on it. The
                // player has already been teleported by the backend; a bad result just costs the
                // welcome message + visit count, so don't alarm them - a log line is enough.
                WarpVelocity.getLogger().warn("Dropped a malformed teleport-result message from backend {}: {}",
                    backend.getServerInfo().getName(), e.getMessage());
                return;
            }

            Warp warp = WarpManager.resolveWarp(response.warpName(), player.getUniqueId());
            if (warp != null) {
                // Route the visit through the store so it is batched and flushed via the crash-safe
                // seam (root cause #3), not persisted on every single teleport.
                WarpManager.recordVisit(warp);

                // getWelcomeMessage() already carries its own formatting (the default starts with
                // <blue>); don't prepend another <blue> or a custom-coloured message gets a dead tag.
                player.sendRichMessage(warp.getWelcomeMessage());
            }
        }
        else if (eventChannel.equals(ChannelIdentifiers.PLAYER_LOCATION_CHANNEL_ID)) {
            String serverName = backend.getServerInfo().getName();

            final PlayerLocationMessage.Result result;
            try {
                result = PlayerLocationMessage.read(event.getData());
            } catch (MalformedMessageException e) {
                // Root cause #2: reject a skewed/corrupt message. Here the player is actively waiting
                // for a create/move that now won't happen, so give them honest feedback.
                WarpVelocity.getLogger().warn("Dropped a malformed player-location message from backend {}: {}",
                    serverName, e.getMessage());
                if (player != null) {
                    player.sendRichMessage("<red>Your warp request failed - the server may be updating. Please try again shortly.");
                }
                return;
            }
            LocationActionSubchannel subchannel = result.subchannel();

            switch (subchannel) {
                case LocationActionSubchannel.MOVE -> moveWarp(result, serverName, player);
                case LocationActionSubchannel.CREATE_PUBLIC, LocationActionSubchannel.CREATE_PRIVATE -> createWarp(result, serverName, player);
                default -> player.sendMessage(Component.text("Unknown subchannel: " + subchannel, NamedTextColor.RED));
            }
        }
        // MISC_ID: consumed above so a client cannot forward it on; the proxy has no incoming MISC handler.
    }

    private void createWarp(PlayerLocationMessage.Result result, String serverName, Player creator) {
        Warp.Type warpType = result.subchannel().equals(LocationActionSubchannel.CREATE_PUBLIC) ? Warp.Type.PUBLIC : Warp.Type.PRIVATE;

        // SEC-3: re-check the create permission at the message boundary. A forged plugin message
        // reaches this handler having bypassed the command-layer permission gate; the create path
        // must not trust that the send was authorised.
        Permission requiredPerm = warpType == Warp.Type.PUBLIC ? Permission.CREATE_PUBLIC : Permission.CREATE_PRIVATE;
        if (!creator.hasPermission(requiredPerm.getNode())) {
            creator.sendRichMessage("<red>You do not have permission to create this warp");
            return;
        }

        Warp newWarp = new Warp(
            creator.getUniqueId(),
            creator.getUsername(),
            result.warpName(),
            serverName,
            result.warpLocation(),
            warpType,
            Instant.now()
        );

        try {
            WarpManager.addWarp(newWarp);
        } catch (Exception e) {
            creator.sendRichMessage("<red>Failed to create warp!");
            return;
        }

        creator.sendRichMessage("<green>Warp \"%s\" has been created!".formatted(newWarp.getName()));

        if (warpType.equals(Warp.Type.PRIVATE) && !creator.hasPermission(Permission.IGNORE_PRIVATE_WARPS_LIMIT.getNode())) {
            final int privateLimit = ConfigManager.resolvePrivateWarpLimit(creator).limit();
            final int creatorPrivateWarpCount = WarpManager.getWarps(w -> w.isCreator(creator) && w.isOfType(Warp.Type.PRIVATE)).size();

            creator.sendRichMessage("<gray>You have <aqua>" + (privateLimit - creatorPrivateWarpCount) + "</aqua> private warps remaining");
        }

        boolean isFirstModifiableWarp = WarpManager.getWarps(WarpPredicates.modifiableBy(creator)).size() == 1;
        if (isFirstModifiableWarp) {
            creator.getCurrentServer().ifPresent(serverConnection -> {
                serverConnection.sendPluginMessage(
                    ChannelIdentifiers.MISC_ID,
                    MiscMessage.serialise(MiscMessage.Subchannel.UPDATE_COMMANDS)
                );
            });
        }
    }

    private void moveWarp(PlayerLocationMessage.Result result, String serverName, Player creator) {
        // SEC-3: re-check modifiability at the message boundary. Without this a forged MOVE message
        // lets any player relocate ANY warp (drag a public landmark to the void), because updateWarp
        // performs no ownership check of its own and the command-layer check is bypassed.
        Warp warp = WarpManager.resolveWarp(result.warpName(), creator.getUniqueId());
        if (warp == null) {
            creator.sendRichMessage("<red>Warp '%s' does not exist".formatted(result.warpName()));
            return;
        }
        if (!warp.isModifiable(creator)) {
            creator.sendRichMessage("<red>You do not have access to modify that warp");
            return;
        }

        SimpleLocation newLocation = result.warpLocation();
        WarpManager.updateWarp(warp, w -> {
            w.setLocation(newLocation);
            w.setServer(serverName);
        }, creator, "Warp successfully moved");
    }
}