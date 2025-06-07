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

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Rename {
    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType MANUAL_PREFIX =
        new SimpleCommandExceptionType(() -> "The renamed warp will be automatically given the 'zzz' prefix, please just provide the warp name");

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("rename")
            // This has to be a string argument because a greedy arg can't have anything after it
            .then(BrigadierCommand.requiredArgumentBuilder("current_name", StringArgumentType.string())
                .suggests(Rename::suggestCurrName)
                .then(BrigadierCommand.requiredArgumentBuilder("new_name", StringArgumentType.greedyString())
                    .executes(Rename::execute)
                )
            );
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        final String currName = context.getArgument("current_name", String.class);
        final String newName = context.getArgument("new_name", String.class);

        Warp currWarp = WarpManager.getWarp(currName);
        if (currWarp == null) throw WARP_NOT_FOUND.create();
        if (currWarp.isOfType(Warp.Type.PRIVATE) && newName.startsWith("zzz")) {
           throw MANUAL_PREFIX.create();
        }

        WarpManager.updateWarp(currName, warp -> warp.setName(newName), sender);
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestCurrName(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player player)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        List<String> warpNames = WarpManager.getAllModifiableWarpNames(player);
        var warpSuggester = new WarpSuggester(warpNames, input);
        List<String> suggestions = warpSuggester.getSuggestions();

        // curr_name has to be a string arg, so wrap multi-word suggestions in quotes
        suggestions.forEach(suggestion -> {
            if (suggestion.contains(" ")) builder.suggest("\"" + suggestion + "\"");
            else builder.suggest(suggestion);
        });
        return builder.buildFuture();
    }
}
