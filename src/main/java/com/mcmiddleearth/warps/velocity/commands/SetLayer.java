package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.WarpIcon;
import com.mcmiddleearth.warps.velocity.commands.helpers.CommandUtils;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpPredicates;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpSuggester;
import com.mcmiddleearth.warps.velocity.config.ConfigManager;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class SetLayer {
    private static final SimpleCommandExceptionType INVALID_LAYER =
        new SimpleCommandExceptionType(() -> "Invalid warp layer");

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("setLayer")
            .requires(requirement)
            .then(BrigadierCommand.requiredArgumentBuilder("warp", StringArgumentType.string())
                .suggests(SetLayer::suggestWarpName)
                .then(BrigadierCommand.requiredArgumentBuilder("layer", StringArgumentType.word())
                    .suggests(SetLayer::suggestLayer)
                    .executes(SetLayer::execute)
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
            "warp"
        ).value();

        List<String> layerKeys = ConfigManager.getConfig().layerKeys();
        layerKeys.add("default");

        final String layerKey = context.getArgument("layer", String.class);
        boolean isLayerKeyValid = layerKeys.contains(layerKey);
        if (!isLayerKeyValid) {
            throw INVALID_LAYER.create();
        }

        final String prevLayerKey = warp.getLayer();
        warp.setLayer(layerKey);

        try {
            WarpManager.saveWarp(warp);
            sender.sendRichMessage("<green> Updated the layer for '%s' from '%s' to '%s'".formatted(warp.getName(), prevLayerKey, layerKey));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            sender.sendRichMessage("<red>" + e.getMessage());
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestWarpName(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        WarpSuggester.suggest(builder, warp -> warp.isOfType(Warp.Type.PUBLIC), true);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestLayer(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player)) {
            return Suggestions.empty();
        }

        String input = builder.getRemaining().toUpperCase();
        List<String> layerKeys = ConfigManager.getConfig().layerKeys();
        layerKeys.add("default");

        layerKeys.forEach(layerKey -> {
           if (layerKey.startsWith(input)) {
               builder.suggest(layerKey);
           }
        });

        return builder.buildFuture();
    }
}