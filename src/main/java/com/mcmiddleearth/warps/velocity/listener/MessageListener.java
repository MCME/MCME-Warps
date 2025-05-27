package com.mcmiddleearth.warps.velocity.listener;

import com.mcmiddleearth.warps.core.CreateSubchannels;
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
        if (!ChannelIdentifiers.CREATE_CHANNEL_ID.equals(event.getIdentifier())) {
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
        Warp.Type warpType = result.subchannel().equals(CreateSubchannels.CREATE_PUBLIC) ? Warp.Type.PUBLIC : Warp.Type.PRIVATE;

        Warp newWarp = new Warp(creator.getUniqueId(), result.warpName(), serverName, result.warpLocation(), warpType);

        boolean addResult = WarpManager.addWarp(newWarp);
        if (addResult) {
            creator.sendMessage(Component.text("Warp created!", NamedTextColor.GREEN));
        } else {
            creator.sendMessage(Component.text("Failed to create warp", NamedTextColor.RED));
        }
    }
}