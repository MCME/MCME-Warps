package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.velocity.commands.helpers.WarpSuggester;
import com.mcmiddleearth.warps.velocity.commands.helpers.CommandUtils;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpPredicates;
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

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class SetPrivate {
    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType ALREADY_PRIVATE =
        new SimpleCommandExceptionType(() -> "This warp is already private");

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("setPrivate")
            .requires(requirement)
            .then(BrigadierCommand.requiredArgumentBuilder("warp-name", StringArgumentType.greedyString())
                .suggests(SetPrivate::suggest)
                .executes(SetPrivate::execute)
            );
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        final String warpName = context.getArgument("warp-name", String.class);
        // SEC-4: require modifiability, not just the flat set-private permission - otherwise any
        // holder could privatise a public landmark, denying it to the whole network (DoS).
        // For a public warp isModifiable is true only with override.modify, matching the README.
        Warp warp = CommandUtils.getWarp(context, "warp-name", WarpPredicates.modifiableBy(sender)).value();
        if (warp.isOfType(Warp.Type.PRIVATE)) throw ALREADY_PRIVATE.create();

        // Q: Add the zzz-<playerName> prefix???
        return WarpManager.updateWarp(warpName,w -> w.setType(Warp.Type.PRIVATE), sender, "<green>Warp '%s' is now private".formatted(warpName));
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        WarpSuggester.suggest(builder, warp -> warp.isOfType(Warp.Type.PUBLIC));
        return builder.buildFuture();
    }
}
