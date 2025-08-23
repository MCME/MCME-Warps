package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.WarpTag;
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
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Tag {
    private static final SimpleCommandExceptionType INVALID_TAG =
        new SimpleCommandExceptionType(() -> "Invalid warp tag");

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("setTag")
            .requires(sender -> sender.hasPermission(Permission.TAG.getNode()))
            .then(BrigadierCommand.requiredArgumentBuilder("warp", StringArgumentType.string())
                .suggests(Tag::suggestWarpName)
                .then(BrigadierCommand.requiredArgumentBuilder("tag", StringArgumentType.word())
                    .suggests(Tag::suggestTag)
                    .executes(Tag::execute)
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
            "warp",
            WarpPredicates.modifiableBy(sender)
        ).value();

        final String strWarpTag = context.getArgument("tag", String.class);
        final WarpTag warpTag = getWarpTag(strWarpTag);
        warp.setTag(warpTag);

        try {
            WarpManager.saveWarp(warp);
            sender.sendRichMessage("<green> Updated the tag for \"%s\" to %s".formatted(warp.getName(), strWarpTag));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            sender.sendRichMessage("<red>" + e.getMessage());
            return 0;
        }
    }

    private static WarpTag getWarpTag(String tag) throws CommandSyntaxException {
        try {
            return WarpTag.valueOf(tag);
        } catch (Exception e) {
            throw INVALID_TAG.create();
        }
    }

    private static CompletableFuture<Suggestions> suggestWarpName(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        Map<String, String> warpNames = WarpManager.getWarpNames(w -> w.isOfType(Warp.Type.PUBLIC));
        var warpSuggester = new WarpSuggester(warpNames, input);
        warpSuggester.getSuggestions().forEach(suggestion -> builder.suggest("\"" + suggestion + "\""));

        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTag(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        EnumSet.allOf(WarpTag.class).forEach(tag -> {
            String tagName = tag.name();
            if (tagName.startsWith(input)) {
                builder.suggest(tagName);
            }
        });
        return builder.buildFuture();
    }
}