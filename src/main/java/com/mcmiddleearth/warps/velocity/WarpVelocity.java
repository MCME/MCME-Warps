package com.mcmiddleearth.warps.velocity;

import com.google.inject.Inject;
import com.mcmiddleearth.warps.core.Channels;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "warp-velocity",
    name = "warp-velocity",
    // version = BuildConstants.VERSION
    // Q: Is this used anywhere/needed?
    version = "1.0"
)
public class WarpVelocity {

    public static final MinecraftChannelIdentifier MAIN_ID = MinecraftChannelIdentifier.from(Channels.MAIN);

    private final ProxyServer proxy;
//    private final Logger logger;
//    private final Path dataDirectory;

    @Inject
    public WarpVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
//        this.logger = logger;
//        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(MAIN_ID);

        CommandManager commandManager = proxy.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("testWarp")
            .aliases("warpFactions")
            .plugin(this)
            .build();

        BrigadierCommand commandToRegister = TestWarpCommand.createBrigadierCommand(proxy);
        commandManager.register(commandMeta, commandToRegister);
    }
}