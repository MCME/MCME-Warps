package com.mcmiddleearth.warps.velocity.listener;

import com.mcmiddleearth.warps.core.LocationActionSubchannel;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.messageprotocols.PlayerLocationMessage;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class MessageListener {
    @Subscribe
    public void onPluginMessageFromBackend(PluginMessageEvent event) {
        if (!ChannelIdentifiers.PLAYER_LOCATION_CHANNEL_ID.equals(event.getIdentifier())) {
            return;
        }

        if (!(event.getSource() instanceof ServerConnection backend)) {
            return;
        }

        // Mark PluginMessage as handled, indicating that the contents
        // should not be forwarding to their original destination.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        Player creator = backend.getPlayer();
        String serverName = backend.getServerInfo().getName();

        PlayerLocationMessage.Result result = PlayerLocationMessage.read(event.getData());
        LocationActionSubchannel subchannel = result.subchannel();

        switch (subchannel) {
            case LocationActionSubchannel.MOVE -> moveWarp(result, serverName, creator);
            case LocationActionSubchannel.CREATE_PUBLIC, LocationActionSubchannel.CREATE_PRIVATE -> createWarp(result, serverName, creator);
            default -> creator.sendMessage(Component.text("Unknown subchannel: " + subchannel, NamedTextColor.RED));
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

        boolean addResult = WarpManager.addWarp(newWarp);
        Component message = addResult
            ? Component.text("Warp created!", NamedTextColor.GREEN)
            : Component.text("Failed to create warp", NamedTextColor.RED);

        creator.sendMessage(message);
    }

    private void moveWarp(PlayerLocationMessage.Result result, String serverName, Player creator) {
        SimpleLocation newLocation = result.warpLocation();
        WarpManager.updateWarp(result.warpName(), warp -> {
            warp.setLocation(newLocation);
            warp.setServer(serverName);
        }, creator);
    }
}