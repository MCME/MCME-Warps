package com.mcmiddleearth.warps.velocity;

import com.google.inject.Inject;
import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.TeleportMessage;
import com.mcmiddleearth.warps.velocity.commands.WarpCommand;
import com.mcmiddleearth.warps.velocity.warps.Warp;
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
        CommandMeta commandMeta = commandManager.metaBuilder("warp")
            .plugin(this)
            .build();

        LiteralCommandNode<CommandSource> commandNode = BrigadierCommand.literalArgumentBuilder("warp")
            // Q: What to suggest after '/warp ' -> prioritise warp names, before CRUD
            .then(WarpCommand.register(proxy))
//            .then(Random.register())
//            .then(Create.register())
//            .then(PrivateCreate.register())
//            .then(Rename.register())
//            .then(Delete.register())
            .build();

        // TODO: getUsage()
        System.out.println(commandNode.getUsageText());

        commandManager.register(commandMeta, new BrigadierCommand(commandNode));

        // Fake warps for testing commands
        Warp a = new Warp("factionsWarp2", "factions", new TeleportMessage.TeleportData("world", -50.0, 100.0, -12.4, -91f, 26f));
        Warp b = new Warp("factionsWarp", "factions", new TeleportMessage.TeleportData("world", -46.0, 96.0, -16.4, -91f, 26f));
        Warp c = new Warp("lobbyWarp",    "lobby",    new TeleportMessage.TeleportData("world", -46.0, 120.0, -16.4, -91f, 26f));
        WarpManager.saveWarp(a);
        WarpManager.saveWarp(b);
        WarpManager.saveWarp(c);
    }
}