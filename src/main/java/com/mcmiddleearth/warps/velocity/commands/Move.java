package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.LocationActionSubchannel;
import com.mcmiddleearth.warps.core.messageprotocols.RequestLocationMessage;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpSuggester;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Move {
    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType NOT_ALLOWED =
        new SimpleCommandExceptionType(() -> "You are not allowed to perform this action");

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("move")
            .then(BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.greedyString())
                .suggests(Move::suggest)
                .executes(Move::execute)
            );
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        final String warpName = context.getArgument("name", String.class);
        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) {
            throw WARP_NOT_FOUND.create();
        }

        // Q: Integrate this directly into WarpManager?
        // - only if always will be using either usable or modifiable
        //   if so, then it can lead to bugs easily by forgetting this check in each command!!!
        if (!warp.isModifiable(player)) {
            throw NOT_ALLOWED.create();
        }

        // Send plugin message requesting player's Location
        player.getCurrentServer().ifPresentOrElse(serverConnection -> {
            player.sendMessage(Component.text("Moving warp..."));

            boolean status = serverConnection.sendPluginMessage(
                ChannelIdentifiers.PLAYER_LOCATION_CHANNEL_ID,
                RequestLocationMessage.serialise(LocationActionSubchannel.MOVE, warpName)
            );

            // TODO
            // if (!status) { }
        }, () -> {
            // TODO: Report no connections
        });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player player)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        List<String> warpNames = WarpManager.getAllModifiableWarpNames(player);
        var warpSuggester = new WarpSuggester(warpNames, input);
        List<String> suggestions = warpSuggester.getSuggestions();

        // Q: Add a tooltip? Display server/word?, creator?, region?
        suggestions.forEach(builder::suggest);
        return builder.buildFuture();
    }
}
