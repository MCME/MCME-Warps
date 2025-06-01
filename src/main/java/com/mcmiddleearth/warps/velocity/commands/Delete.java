package com.mcmiddleearth.warps.velocity.commands;

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

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Delete {
    // TODO: Extract
    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType NOT_ALLOWED =
        new SimpleCommandExceptionType(() -> "You are not allowed to perform this action");

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("delete")
            .then(BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.greedyString())
                .suggests(Delete::suggest)
                .executes(Delete::execute)
            );
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        // TODO: Make this a re-usable helper
        final String warpName = context.getArgument("name", String.class);
        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) {
            throw WARP_NOT_FOUND.create();
        }

        if (!warp.isModifiable(player)) {
            throw NOT_ALLOWED.create();
        }

        WarpManager.deleteWarp(warp);
        player.sendRichMessage("<green>Warp '%s' deleted".formatted(warpName));
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
