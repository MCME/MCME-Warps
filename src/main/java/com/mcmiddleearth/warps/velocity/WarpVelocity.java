package com.mcmiddleearth.warps.velocity;

import com.google.inject.Inject;
import com.mcmiddleearth.warps.velocity.commands.*;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpHelper;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpPredicates;
import com.mcmiddleearth.warps.velocity.config.ConfigManager;
import com.mcmiddleearth.warps.velocity.listener.MessageListener;
import com.mcmiddleearth.warps.velocity.warps.PlayerNameResolver;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.function.Predicate;

@Plugin(
    id = "mcme-warps-velocity",
    name = "MCME-Warps-Velocity",
    version = BuildConstants.VERSION,
    authors = {"_Drayz_"}
)
public class WarpVelocity {

    private static WarpVelocity instance;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataFolder;
    private PlayerNameResolver playerNameResolver;

    @Inject
    public WarpVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        instance = this;

        this.proxy = proxy;
        this.logger = logger;
        this.dataFolder = dataDirectory;
    }

    private static WarpVelocity getInstance() { return instance; }

    public static Path getDataFolder() { return getInstance().dataFolder; }
    public static Logger getLogger() { return getInstance().logger; }
    public static ProxyServer getProxy() { return getInstance().proxy; }
    public static PlayerNameResolver getPlayerNameResolver() { return getInstance().playerNameResolver; }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getEventManager().register(this, new MessageListener());

        this.playerNameResolver = new PlayerNameResolver(dataFolder.resolve("profiles.db"));
        proxy.getEventManager().register(this, playerNameResolver);

        proxy.getChannelRegistrar().register(ChannelIdentifiers.MAIN_ID);
        proxy.getChannelRegistrar().register(ChannelIdentifiers.PLAYER_LOCATION_CHANNEL_ID);

        ConfigManager.loadConfig();
        WarpManager.loadAllWarps();

        CommandManager commandManager = proxy.getCommandManager();

        CommandMeta warpCommandMeta = commandManager.metaBuilder("warp")
            .aliases("to")
            .plugin(this)
            .build();
        LiteralCommandNode<CommandSource> warpCommandNode = BrigadierCommand.literalArgumentBuilder("warp")
            .executes(WarpHelper::execute)
            .then(RandomCommand.register( WarpRequirements.hasPerm(Permission.RANDOM) ))
            // Greedy required arg goes at the bottom
            .then(To.register( WarpRequirements.hasPerm(Permission.WARP) ))
            .build();
        commandManager.register(warpCommandMeta, new BrigadierCommand(warpCommandNode));

        CommandMeta localwarpCommandMeta = commandManager.metaBuilder("localwarp")
            .aliases("lwarp")
            .plugin(this)
            .build();
        LiteralCommandNode<CommandSource> localWarpCommandNode = BrigadierCommand.literalArgumentBuilder("localwarp")
            .then(LocalWarp.register( WarpRequirements.hasPerm(Permission.LOCAL_WARP) ))
            .build();
        commandManager.register(localwarpCommandMeta, new BrigadierCommand(localWarpCommandNode));

        CommandMeta managerCommandMeta = commandManager.metaBuilder("warpmanager")
            .aliases("wmanage")
            .plugin(this)
            .build();
        LiteralCommandNode<CommandSource> managerCommandNode = BrigadierCommand.literalArgumentBuilder("warpmanager")
            .then(Create.register( WarpRequirements.hasPerm(Permission.CREATE_PUBLIC) ))
            .then(PrivateCreate.register( WarpRequirements.hasPerm(Permission.CREATE_PRIVATE) ))
            .then(SetPublic.register( WarpRequirements.hasPerm(Permission.SET_PUBLIC) ))
            .then(SetPrivate.register( WarpRequirements.hasPerm(Permission.SET_PRIVATE) ))
            .then(Reload.register( WarpRequirements.hasPerm(Permission.RELOAD) ))
            .then(SetIcon.register( WarpRequirements.hasPerm(Permission.SET_ICON) ))
            .then(ListCommand.register( WarpRequirements.hasPerm(Permission.LIST) ))
            .then(PrivateListCommand.register( WarpRequirements.hasPerm(Permission.LIST) ))
            .then(Assets.register( WarpRequirements.hasPerm(Permission.LIST) ))
            // Commands that require modifiable warps
            .then(Delete.register( WarpRequirements.hasPermAndWarps(Permission.DELETE) ))
            .then(Rename.register( WarpRequirements.hasPermAndWarps(Permission.RENAME) ))
            .then(Move.register( WarpRequirements.hasPermAndWarps(Permission.MOVE) ))
            .then(Members.register( WarpRequirements.hasPermAndWarps(Permission.MANAGE_MEMBERS)) )
            .then(SetWelcome.register( WarpRequirements.hasPermAndWarps(Permission.WELCOME) ))
            .build();
        commandManager.register(managerCommandMeta, new BrigadierCommand(managerCommandNode));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        WarpManager.saveAllWarps();
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent event) {
        // Visits are only saved on shutdown, so this is needed
        WarpManager.saveWarpVisits();

        // Reload the config & warps
        ConfigManager.loadConfig();
        WarpManager.loadAllWarps();
    }

    private static final class WarpRequirements {
        private WarpRequirements() {}

        public static Predicate<CommandSource> hasPerm(Permission perm) {
            return sender -> sender.hasPermission(perm.getNode());
        }

        public static Predicate<CommandSource> hasModifiableWarp() {
            return sender -> {
                if (sender instanceof Player player) {
                    return WarpManager.getWarps(WarpPredicates.modifiableBy(player)).size() > 0;
                }
                return true;
            };
        }

        public static Predicate<CommandSource> hasPermAndWarps(Permission perm) {
            return hasPerm(perm).and(hasModifiableWarp());
        }
    }
}