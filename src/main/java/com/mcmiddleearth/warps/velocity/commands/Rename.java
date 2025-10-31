package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.velocity.commands.helpers.CommandUtils;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpPredicates;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpSuggester;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class Rename {
    private static final DynamicCommandExceptionType INVALID_PRIVATE_PREFIX =
        new DynamicCommandExceptionType(name -> new LiteralMessage("A private warp must start with zzz-" + name + "-"));

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("rename")
            .requires(requirement)
            // This has to be a string argument because a greedy arg can't have anything after it
            .then(BrigadierCommand.requiredArgumentBuilder("current_name", StringArgumentType.string())
                .suggests(Rename::suggestCurrName)
                .then(BrigadierCommand.requiredArgumentBuilder("new_name", StringArgumentType.greedyString())
                    .suggests(Rename::suggestNewName)
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

        // getWarp ensures current_name is valid
        Warp currWarp = CommandUtils.getWarp(
            context,
            "current_name",
            WarpPredicates.modifiableBy(sender)
        ).value();

        final String newName = context.getArgument("new_name", String.class);

        if (currWarp.isOfType(Warp.Type.PRIVATE) && !newName.startsWith("zzz-" + sender.getUsername() + "-")) {
            throw INVALID_PRIVATE_PREFIX.create(sender.getUsername());
        }

        CommandUtils.validateWarpName(newName);

        return WarpManager.updateWarp(currWarp.getName(),
            warp -> warp.setName(newName),
            sender,
            "<green>Renamed warp '%s' to '%s'".formatted(currWarp.getName(), newName)
        );
    }

    private static CompletableFuture<Suggestions> suggestCurrName(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        WarpSuggester.suggest(builder, WarpPredicates.modifiableBy(sender), true);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestNewName(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        if (!input.isEmpty()) {
            return Suggestions.empty();
        }

        final String currName = context.getArgument("current_name", String.class);
        builder.suggest(currName);

        Warp warp = WarpManager.getWarp(currName);
        if (warp != null && warp.isOfType(Warp.Type.PRIVATE)) {
            final String privatePrefix = "zzz-" + sender.getUsername() + "-";
            builder.suggest(privatePrefix);
        }

        return builder.buildFuture();
    }
}
