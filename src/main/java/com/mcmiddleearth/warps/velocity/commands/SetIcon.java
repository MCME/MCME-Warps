package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.WarpIcon;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class SetIcon {
    private static final SimpleCommandExceptionType INVALID_ICON =
        new SimpleCommandExceptionType(() -> "Invalid warp icon");

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("setIcon")
            .requires(requirement)
            .then(BrigadierCommand.requiredArgumentBuilder("warp", StringArgumentType.string())
                .suggests(SetIcon::suggestWarpName)
                .then(BrigadierCommand.requiredArgumentBuilder("icon", StringArgumentType.word())
                    .suggests(SetIcon::suggestIcon)
                    .executes(SetIcon::execute)
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

        final String strWarpIcon = context.getArgument("icon", String.class);
        final WarpIcon warpIcon = getWarpIcon(strWarpIcon);
        warp.setIcon(warpIcon);

        try {
            WarpManager.saveWarp(warp);
            sender.sendRichMessage("<green> Updated the icon for \"%s\" to %s".formatted(warp.getName(), strWarpIcon));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            sender.sendRichMessage("<red>" + e.getMessage());
            return 0;
        }
    }

    private static WarpIcon getWarpIcon(String icon) throws CommandSyntaxException {
        try {
            return WarpIcon.valueOf(icon);
        } catch (Exception e) {
            throw INVALID_ICON.create();
        }
    }

    private static CompletableFuture<Suggestions> suggestWarpName(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        WarpSuggester.suggest(builder, warp -> warp.isOfType(Warp.Type.PUBLIC), true);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestIcon(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        EnumSet.allOf(WarpIcon.class).forEach(icon -> {
            String iconName = icon.name();
            if (iconName.startsWith(input)) {
                builder.suggest(iconName);
            }
        });
        return builder.buildFuture();
    }
}