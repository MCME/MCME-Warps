package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.messageprotocols.TeleportMessage;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.Permission;
import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class RandomCommand {
    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType NO_WARPS =
        new SimpleCommandExceptionType(() -> "There are no warps to teleport to");

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("random")
            .requires(sender -> sender.hasPermission(Permission.RANDOM.getNode()))
            .executes(RandomCommand::execute);
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        List<String> warps = WarpManager.getAllUsableWarpNames(player);
        if (warps.isEmpty()) {
            throw NO_WARPS.create();
        }

        Random rand = new Random();
        String warpName = warps.get(rand.nextInt(warps.size()));

        // TODO: Extract the below to a shared location
        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) {
            throw WARP_NOT_FOUND.create();
        }

        Optional<ServerConnection> optCurrServer = player.getCurrentServer();
        if (optCurrServer.isEmpty()) {
            player.sendMessage(Component.text("No server connection found! Unable to perform warp."));
            return Command.SINGLE_SUCCESS;
        }

        ServerConnection currServer = optCurrServer.get();
        final String currServerName = currServer.getServer().getServerInfo().getName();
        final String targetServerName = warp.getServer();

        if (currServerName.equalsIgnoreCase(targetServerName)) {
            sendTeleportMessage(currServer, warp);
            return Command.SINGLE_SUCCESS;
        }

        ProxyServer proxy = WarpVelocity.getInstance().getProxy();
        Optional<RegisteredServer> optTargetServer = proxy.getServer(targetServerName);
        optTargetServer.ifPresent(targetServer -> {
            player.createConnectionRequest(targetServer).connectWithIndication()
                // Q: Does this block the main thread?
                .whenCompleteAsync((success, _) -> {
                    if (!success) {
                        player.sendMessage(
                            Component.text("Aborting teleport, failed to connect to server '" + targetServerName + "'", NamedTextColor.RED)
                        );
                        return;
                    }

                    player.sendMessage(Component.text("Changed server!"));
                    sendTeleportMessage(targetServer, warp);
                });
        });

        return Command.SINGLE_SUCCESS;
    }

    private static void sendTeleportMessage(ChannelMessageSink serverConnection, Warp warp) {
        SimpleLocation data = warp.getLocation();

        // Messaging the backend server, using the sender's connection
        serverConnection.sendPluginMessage(
            ChannelIdentifiers.MAIN_ID,
            TeleportMessage.serialise(TeleportMessage.Subchannel.TELEPORT, data)
        );
    }
}
