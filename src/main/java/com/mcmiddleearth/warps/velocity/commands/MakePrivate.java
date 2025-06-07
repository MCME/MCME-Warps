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

public class MakePrivate {
    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType ALREADY_PRIVATE =
        new SimpleCommandExceptionType(() -> "This warp is already private");

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("makePrivate")
            .then(BrigadierCommand.requiredArgumentBuilder("warp-name", StringArgumentType.greedyString())
                // TODO: Only staff
                //.requires()
                .suggests(MakePrivate::suggest)
                .executes(MakePrivate::execute)
            );
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        final String warpName = context.getArgument("warp-name", String.class);
        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) throw WARP_NOT_FOUND.create();
        if (warp.isOfType(Warp.Type.PRIVATE)) throw ALREADY_PRIVATE.create();

        // Q: Add the zzz-<playerName> prefix???
        WarpManager.updateWarp(warpName,w -> w.setType(Warp.Type.PRIVATE), sender);
        sender.sendRichMessage("<green>Warp '%s' is now private".formatted(warpName));

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        List<String> warpNames = WarpManager.getWarpNames(warp -> warp.isOfType(Warp.Type.PUBLIC));
        var warpSuggester = new WarpSuggester(warpNames, input);
        List<String> suggestions = warpSuggester.getSuggestions();

        suggestions.forEach(builder::suggest);
        return builder.buildFuture();
    }
}
