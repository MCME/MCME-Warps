package com.mcmiddleearth.warps.velocity;

import com.google.inject.Inject;
import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.PlayerLocationMessage;
import com.mcmiddleearth.warps.velocity.commands.Create;
import com.mcmiddleearth.warps.velocity.commands.WarpCommand;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
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

    public static final MinecraftChannelIdentifier MAIN_ID = MinecraftChannelIdentifier.from(Channels.WARP);
    public static final MinecraftChannelIdentifier CREATE_CHANNEL_ID = MinecraftChannelIdentifier.from(Channels.CREATE_WARP);

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
        proxy.getChannelRegistrar().register(MAIN_ID);
        proxy.getChannelRegistrar().register(CREATE_CHANNEL_ID);

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

    // TODO: Extract
    @Subscribe
    public void onPluginMessageFromBackend(PluginMessageEvent event) {
        // Check if the identifier matches first, no matter the source.
        if (!CREATE_CHANNEL_ID.equals(event.getIdentifier())) {
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