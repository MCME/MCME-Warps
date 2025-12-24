package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.messageprotocols.TeleportMessage;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.commands.helpers.ServerConnectUtils;
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
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

public class RandomCommand {
    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType NO_WARPS =
        new SimpleCommandExceptionType(() -> "Sorry! You do not have access to any warps.");

    private static final Set<String> PUBLIC_SERVERS = Set.of("world", "moria");

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("random")
            .requires(requirement)
            .executes(RandomCommand::execute);
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        ArrayList<Warp> warpsList = new ArrayList<>(
            WarpManager.getWarps(
            w -> w.isOfType(Warp.Type.PUBLIC) && PUBLIC_SERVERS.contains(w.getServer())
            ).values()
        );

        if (warpsList.isEmpty()) {
            throw NO_WARPS.create();
        }

        int randomIndex = RandomGenerator.getDefault().nextInt(warpsList.size());
        Warp warp = warpsList.get(randomIndex);

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
            sendTeleportMessage(currServer, warp, true);
            return Command.SINGLE_SUCCESS;
        }

        ServerConnectUtils.connectPlayerToServer(
            player,
            targetServerName,
            newConnection -> sendTeleportMessage(newConnection, warp, false)
        );

        return Command.SINGLE_SUCCESS;
    }

    private static void sendTeleportMessage(ServerConnection serverConnection, Warp warp, Boolean isSameServer) {
        SimpleLocation data = warp.getLocation();

        // Messaging the backend server, using the sender's connection
        serverConnection.sendPluginMessage(
            ChannelIdentifiers.MAIN_ID,
            TeleportMessage.serialise(
                isSameServer
                    ? TeleportMessage.Subchannel.SAME_SERVER
                    : TeleportMessage.Subchannel.DIFF_SERVER,
                warp.getName(),
                data
            ));
    }
}
