package com.mcmiddleearth.warps.velocity.listener;

import com.mcmiddleearth.warps.core.messageprotocols.*;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpPredicates;
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

        Warp newWarp = new Warp(
            creator.getUniqueId(),
            result.warpName(),
            serverName,
            result.warpLocation(),
            warpType
        );

        try {
            WarpManager.addWarp(newWarp);
        } catch (Exception e) {
            creator.sendRichMessage("<red>Failed to create warp!");
            return;
        }

        creator.sendRichMessage("<green>Warp \"%s\" has been created!".formatted(newWarp.getName()));

        if (warpType.equals(Warp.Type.PRIVATE)) {
            final int privateLimit = ConfigManager.getConfig().privateWarpLimit();
            final int creatorPrivateWarpCount = WarpManager.getWarps(w -> w.isCreator(creator) && w.isOfType(Warp.Type.PRIVATE)).size();
            creator.sendRichMessage("<gray>You have " + (privateLimit - creatorPrivateWarpCount) + " private warps remaining");
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
        SimpleLocation newLocation = result.warpLocation();
        WarpManager.updateWarp(result.warpName(), warp -> {
            warp.setLocation(newLocation);
            warp.setServer(serverName);
        }, creator, "Warp successfully moved");
    }
}