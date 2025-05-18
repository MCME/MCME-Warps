package com.mcmiddleearth.warps.velocity.listener;

import com.mcmiddleearth.warps.core.messageprotocols.PlayerLocationMessage;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;

public class MessageListener {
    @Subscribe
    public void onPluginMessageFromBackend(PluginMessageEvent event) {
        // Check if the identifier matches first, no matter the source.
        if (!ChannelIdentifiers.CREATE_CHANNEL_ID.equals(event.getIdentifier())) {
            return;
        }

        // only attempt parsing the data if the source is a backend server
        if (!(event.getSource() instanceof ServerConnection backend)) {
            return;
        }

        // mark PluginMessage as handled, indicating that the contents
        // should not be forwarding to their original destination.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        String serverName = backend.getServerInfo().getName();
        PlayerLocationMessage.Result result = PlayerLocationMessage.read(event.getData());
        Warp newWarp = new Warp(result.warpName(), serverName, result.warpLocation());

        // TODO: Store in different location depending on subchannel == PRIVATE
        // result.subchannel()

        WarpManager.saveWarp(newWarp);

        backend.getPlayer().sendMessage(Component.text("Warp created!"));
    }
}