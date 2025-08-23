package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.velocity.Permission;
import com.mcmiddleearth.warps.velocity.commands.helpers.CommandUtils;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpPredicates;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpSuggester;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class Welcome {
    // /warp welcome <warp> <message>
    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("welcome")
            .requires(requirement)
            .then(BrigadierCommand.requiredArgumentBuilder("warp-name", StringArgumentType.string())
                .suggests(Welcome::suggestWarp)
                .then(BrigadierCommand.requiredArgumentBuilder("welcome-message", StringArgumentType.greedyString())
                    .suggests(Welcome::suggestWelcome)
                    .executes(Welcome::execute)
                )
            );
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        Warp warp = CommandUtils.getWarp(
            context,
            "warp-name",
            WarpPredicates.modifiableBy(sender)
        ).value();
        final String welcomeMessage = context.getArgument("welcome-message", String.class);

        warp.setWelcomeMessage(welcomeMessage);
        sender.sendRichMessage("<green>Welcome message updated for warp " + warp.getName());
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestWarp(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        Map<String, String> warpNames = WarpManager.getWarpNames(warp -> warp.isModifiable(sender));
        var warpSuggester = new WarpSuggester(warpNames, input);
        warpSuggester.getSuggestions().forEach(suggestion -> builder.suggest("\"" + suggestion + "\""));

        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestWelcome(CommandContext<CommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        if (!builder.getRemainingLowerCase().isEmpty()) {
            return Suggestions.empty();
        }

        Warp warp = CommandUtils.getWarp( context, "warp-name").value();
        builder.suggest(warp.getWelcomeMessage());
        return builder.buildFuture();
    }
}
