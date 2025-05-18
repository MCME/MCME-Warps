package com.mcmiddleearth.warps.velocity;

import com.google.inject.Inject;
import com.mcmiddleearth.warps.velocity.commands.Create;
import com.mcmiddleearth.warps.velocity.commands.WarpCommand;
import com.mcmiddleearth.warps.velocity.listener.MessageListener;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
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

    private static WarpVelocity instance;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataFolder;

    @Inject
    public WarpVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        instance = this;

        this.proxy = proxy;
        this.logger = logger;
        this.dataFolder = dataDirectory;
    }

    public static WarpVelocity getInstance() { return instance; }

    // Q: Why getInstance().getDataFolder and not just getDataFolder() directly???
    public Path getDataFolder() { return dataFolder; }
    public Logger getLogger() { return logger; }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getEventManager().register(this, new MessageListener());

        proxy.getChannelRegistrar().register(ChannelIdentifiers.MAIN_ID);
        proxy.getChannelRegistrar().register(ChannelIdentifiers.CREATE_CHANNEL_ID);

        WarpManager.loadAllWarps();

        CommandManager commandManager = proxy.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("warp")
            .plugin(this)
            .build();
        LiteralCommandNode<CommandSource> commandNode = BrigadierCommand.literalArgumentBuilder("warp")
            .then(WarpCommand.register(proxy))
//            .then(Random.register())
            .then(Create.register())
//            .then(PrivateCreate.register())
//            .then(Rename.register())
//            .then(Delete.register())
            .build();
        commandManager.register(commandMeta, new BrigadierCommand(commandNode));
    }
}