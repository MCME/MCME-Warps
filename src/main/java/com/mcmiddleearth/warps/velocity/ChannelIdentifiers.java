package com.mcmiddleearth.warps.velocity;

import com.mcmiddleearth.warps.core.Channels;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

public class ChannelIdentifiers {
    public static final MinecraftChannelIdentifier MAIN_ID = MinecraftChannelIdentifier.from(Channels.WARP);
    public static final MinecraftChannelIdentifier CREATE_CHANNEL_ID = MinecraftChannelIdentifier.from(Channels.CREATE_WARP);
}
