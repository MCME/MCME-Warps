package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.messageprotocols.TeleportMessage;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.Permission;
import com.mcmiddleearth.warps.velocity.commands.helpers.CommandUtils;
import com.mcmiddleearth.warps.velocity.commands.helpers.ServerConnectUtils;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpPredicates;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpSuggester;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class WarpCommand {
    private static final Set<String> SuggestedWarpNames = Set.of(
        "Minas Tirith",
        "Cair Andros",
        "Dol Amroth",
        "Lond Daer Enedh"
    );
    private static final Collection<String> ValidatedSuggestWarpNames = WarpManager.getWarpNames(w -> SuggestedWarpNames.contains(w.getName())).values();

    public static RequiredArgumentBuilder<CommandSource, String> register() {
            return BrigadierCommand.requiredArgumentBuilder("destination", StringArgumentType.greedyString())
                .requires(sender -> sender.hasPermission(Permission.WARP.getNode()))
                .suggests(WarpCommand::suggest)
                .executes(WarpCommand::execute);
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        Warp warp = CommandUtils.getWarp(
            context,
            "destination",
            WarpPredicates.usableBy(sender)
        ).value();

        Optional<ServerConnection> optCurrServer = sender.getCurrentServer();
        if (optCurrServer.isEmpty()) {
            sender.sendMessage(Component.text("No server connection found! Unable to perform warp."));
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
            sender,
            targetServerName,
            targetServer -> sendTeleportMessage(targetServer, warp, false)
        );

        return Command.SINGLE_SUCCESS;
    }

    private static void sendTeleportMessage(ChannelMessageSink serverConnection, Warp warp, Boolean isSameServer) {
        SimpleLocation data = warp.getLocation();

        // Messaging the backend server, using the sender's connection
        serverConnection.sendPluginMessage(
            ChannelIdentifiers.MAIN_ID,
            TeleportMessage.serialise(
                isSameServer
                    ? TeleportMessage.Subchannel.TELEPORT_SAME_SERVER
                    : TeleportMessage.Subchannel.TELEPORT_NEW_SERVER,
                warp.getName(),
                data
            ));
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        boolean isSearchEmpty = input.isEmpty();
        if (isSearchEmpty) {
            // Suggest a few recommended warps - otherwise subcommands like pcreate & random would be lost
            ValidatedSuggestWarpNames.forEach(builder::suggest);
            return builder.buildFuture();
        }

        Map<String, String> warpNames = WarpManager.getAllUsableWarpNames(sender);
        var warpSuggester = new WarpSuggester(warpNames, input);
        Collection<String> suggestions = warpSuggester.getSuggestions();

        suggestions.forEach(builder::suggest);
        return builder.buildFuture();
    }
}



