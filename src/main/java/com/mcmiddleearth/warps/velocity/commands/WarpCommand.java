package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.messageprotocols.TeleportMessage;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.Permission;
import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpSuggester;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class WarpCommand {
    // TODO: Extract
    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType NOT_ALLOWED =
        new SimpleCommandExceptionType(() -> "You are not allowed to perform this action");

    public static RequiredArgumentBuilder<CommandSource, String> register() {
            return BrigadierCommand.requiredArgumentBuilder("destination", StringArgumentType.greedyString())
                .requires(sender -> sender.hasPermission(Permission.WARP.getNode()))
                .suggests(WarpCommand::suggest)
                .executes(WarpCommand::execute);
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            // Q: Easy way to add this to the entire /warp tree? Using permissions?
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        // TODO: Make this a re-usable helper
        final String warpName = context.getArgument("destination", String.class);
        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) {
            throw WARP_NOT_FOUND.create();
        }

        // Q: Integrate this directly into WarpManager?
        // - only if always will be using either usable or modifiable
        //   if so, then it can lead to bugs easily by forgetting this check in each command!!!
        if (!warp.isUsable(player)) {
            throw NOT_ALLOWED.create();
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

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player player)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        boolean isSearchEmpty = input.isEmpty();
        if (isSearchEmpty) {
            // Suggest a few recommended warps - otherwise subcommands like pcreate & random would be lost
            // Q: Use getWarp for these?
            // TODO: Display favourites - If the player has none then display...
            builder.suggest("Minas Tirith");
            builder.suggest("Cair Andros");
            builder.suggest("Dol Amroth");
            // builder.suggest("<warp_name>"); // One alternative
            return builder.buildFuture();
        }

        List<String> warpNames = WarpManager.getAllUsableWarpNames(player);
        var warpSuggester = new WarpSuggester(warpNames, input);
        List<String> suggestions = warpSuggester.getSuggestions();

        // Q: Add a tooltip? Display server/word?, creator?, region?
        suggestions.forEach(builder::suggest);
        return builder.buildFuture();
    }
}



