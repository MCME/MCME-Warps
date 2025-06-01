package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.*;
import com.mcmiddleearth.warps.paper.listener.MessageListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class WarpPaper extends JavaPlugin {

    @Override
    public void onEnable() {
        var listener = new MessageListener(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.WARP, listener);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channels.PLAYER_LOCATION, listener);

        getServer().getMessenger().registerOutgoingPluginChannel(this, Channels.PLAYER_LOCATION);
    }

    @Override
    public void onDisable() {
    }
};