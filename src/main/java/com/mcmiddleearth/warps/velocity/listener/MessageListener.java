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
        if (!(event.getSource() instanceof ServerConnection backend)) {
            return;
        }

        final ChannelIdentifier eventChannel = event.getIdentifier();
        Player player = backend.getPlayer();

        if (eventChannel.equals(ChannelIdentifiers.MAIN_ID)) {
            // Mark PluginMessage as handled, indicating that the contents
            // should not be forwarding to their original destination.
            event.setResult(PluginMessageEvent.ForwardResult.handled());

            TeleportResult.Response response = TeleportResult.read(event.getData());

            Warp warp = WarpManager.getWarp(response.warpName());
            if (warp != null) {
                warp.addVisit();

                final String welcomeMessage = warp.getWelcomeMessage();
                player.sendRichMessage("<blue>" + welcomeMessage);
            }
        }
        else if (eventChannel.equals(ChannelIdentifiers.PLAYER_LOCATION_CHANNEL_ID)) {
            // Mark PluginMessage as handled, indicating that the contents
            // should not be forwarding to their original destination.
            event.setResult(PluginMessageEvent.ForwardResult.handled());

            String serverName = backend.getServerInfo().getName();

            PlayerLocationMessage.Result result = PlayerLocationMessage.read(event.getData());
            LocationActionSubchannel subchannel = result.subchannel();

            switch (subchannel) {
                case LocationActionSubchannel.MOVE -> moveWarp(result, serverName, player);
                case LocationActionSubchannel.CREATE_PUBLIC, LocationActionSubchannel.CREATE_PRIVATE -> createWarp(result, serverName, player);
                default -> player.sendMessage(Component.text("Unknown subchannel: " + subchannel, NamedTextColor.RED));
            }
        }
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
        Warp warp = WarpManager.getWarp(result.warpName());
        if (warp == null) {
            creator.sendRichMessage("<red>Warp '%s' does not exist".formatted(result.warpName()));
            return;
        }
        if (!warp.isModifiable(creator)) {
            creator.sendRichMessage("<red>You do not have access to modify that warp");
            return;
        }

        SimpleLocation newLocation = result.warpLocation();
        WarpManager.updateWarp(result.warpName(), w -> {
            w.setLocation(newLocation);
            w.setServer(serverName);
        }, creator, "Warp successfully moved");
    }
}