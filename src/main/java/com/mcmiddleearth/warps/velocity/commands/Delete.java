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
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Delete {
    private static final SimpleCommandExceptionType NOT_ALLOWED =
        new SimpleCommandExceptionType(() -> "You are not allowed to perform this action");

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("delete")
            .requires(sender -> sender.hasPermission(Permission.DELETE.getNode()))
            .then(BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.greedyString())
                .suggests(Delete::suggest)
                .executes(Delete::execute)
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
            "name",
            WarpPredicates.modifiableBy(sender)
        ).value();

        try {
            WarpManager.deleteWarp(warp);
            sender.sendRichMessage("<green>Warp '%s' deleted".formatted(warp.getName()));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        Map<String, String> warpNames = WarpManager.getAllModifiableWarpNames(sender);
        var warpSuggester = new WarpSuggester(warpNames, input);
        Collection<String> suggestions = warpSuggester.getSuggestions();

        suggestions.forEach(builder::suggest);
        return builder.buildFuture();
    }
}
