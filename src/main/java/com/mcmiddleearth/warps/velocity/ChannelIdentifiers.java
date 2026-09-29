package com.mcmiddleearth.warps.velocity;

import com.mcmiddleearth.warps.core.Channels;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

public class ChannelIdentifiers {
    public static final MinecraftChannelIdentifier MAIN_ID = MinecraftChannelIdentifier.from(Channels.WARP);
    public static final MinecraftChannelIdentifier PLAYER_LOCATION_CHANNEL_ID = MinecraftChannelIdentifier.from(Channels.PLAYER_LOCATION);
    public static final MinecraftChannelIdentifier MISC_ID = MinecraftChannelIdentifier.from(Channels.MISC);
}
