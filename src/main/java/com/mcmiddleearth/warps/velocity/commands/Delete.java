package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.concurrent.CompletableFuture;

public class Delete {
    // TODO: Extract
    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType NOT_ALLOWED =
        new SimpleCommandExceptionType(() -> "You are not allowed to perform this action");

    public static RequiredArgumentBuilder<CommandSource, String> register() {
        return BrigadierCommand.requiredArgumentBuilder("warpName", StringArgumentType.word())
            .suggests(Delete::suggest)
            .executes(Delete::execute);
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        // TODO: Make this a re-usable helper
        final String warpName = context.getArgument("warpName", String.class);
        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) {
            throw WARP_NOT_FOUND.create();
        }

        if (!warp.isModifiable(player)) {
            throw NOT_ALLOWED.create();
        }

        WarpManager.deleteWarp(warp);
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        // .filter(completion -> matchesAnySegment(completion, builder.getRemainingLowerCase()))

        if (!(context.getSource() instanceof Player player)) {
            return Suggestions.empty();
        }

        WarpManager.getAllModifiableWarpNames(player)
            .stream()
            // TODO: Sort by:
            // exact match (case sensitive), exact match (ignore case)
            // contains (case sensitive & insensitive)
            // typos and small changes (like ' and spaces)
            // Priorisation: set priority, same server, same world, (possible addition: visits, popularity)
            // Have a default alphabetical sort (until popularity sort is added?)
            // Fuzzy matching? -> https://github.com/xdrop/fuzzywuzzy
            // Q: Always store warpNames in lowercase?
            .filter(c -> c.toLowerCase().startsWith(builder.getRemainingLowerCase()))
            .forEach(builder::suggest);

        return builder.buildFuture();
    }
}
