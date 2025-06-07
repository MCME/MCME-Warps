package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.velocity.WarpVelocity;
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
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Invite {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("invite")
            .then(BrigadierCommand.requiredArgumentBuilder("warp", StringArgumentType.string())
                .suggests(Invite::suggestWarpName)
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                    .suggests(Invite::suggestPlayer)
                    .executes(Invite::execute)
                )
            );
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        final String warpName = context.getArgument("warp", String.class);
        final String playerName = context.getArgument("player", String.class);

        Player targetPlayer = WarpVelocity.getInstance().getProxy()
            .getPlayer(playerName)
            .orElseThrow(() -> INVALID_PLAYER.create(playerName));

        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) throw WARP_NOT_FOUND.create();
        if (warp.isOfType(Warp.Type.PUBLIC)) throw WARP_IS_PUBLIC.create();
        if (!warp.isModifiable(sender)) throw NOT_ALLOWED.create();

        if (warp.getCreator().equals(targetPlayer.getUniqueId())) {
            throw ALREADY_MEMBER.create();
        }

        Set<UUID> memberIDs = warp.getMembers().keySet();
        if (memberIDs.contains(targetPlayer.getUniqueId())) {
            throw ALREADY_MEMBER.create();
        }

        warp.addPlayer(targetPlayer);
        sender.sendRichMessage("<green>" + targetPlayer.getUsername() + " has been added to warp " + warpName);
        targetPlayer.sendRichMessage("<green>You have been added to warp " + warpName);

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestWarpName(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String input = builder.getRemainingLowerCase();
        List<String> warpNames = WarpManager.getWarpNames(warp -> warp.isOfType(Warp.Type.PRIVATE) && warp.isModifiable(sender));
        var warpSuggester = new WarpSuggester(warpNames, input);
        List<String> suggestions = warpSuggester.getSuggestions();

        // curr_name has to be a string arg, so wrap multi-word suggestions in quotes
        suggestions.forEach(suggestion -> {
            if (suggestion.contains(" ")) builder.suggest("\"" + suggestion + "\"");
            else builder.suggest(suggestion);
        });
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayer(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        // Suggest online players
        String input = builder.getRemainingLowerCase();
        WarpVelocity.getInstance().getProxy().getAllPlayers().forEach(player -> {
            String username = player.getUsername().toLowerCase();
            if (username.startsWith(input)) {
                builder.suggest(player.getUsername());
            }
        });
        return builder.buildFuture();
    }

    private static final DynamicCommandExceptionType INVALID_PLAYER =
        new DynamicCommandExceptionType(name -> new LiteralMessage("Unable to find a player with name '" + name + "'"));

    private static final SimpleCommandExceptionType NOT_ALLOWED =
        new SimpleCommandExceptionType(() -> "You are not allowed to perform this action");

    private static final SimpleCommandExceptionType WARP_NOT_FOUND =
        new SimpleCommandExceptionType(() -> "No warp found with that name");

    private static final SimpleCommandExceptionType ALREADY_MEMBER =
        new SimpleCommandExceptionType(() -> "This player is already a member of the warp!");

    private static final SimpleCommandExceptionType WARP_IS_PUBLIC =
        new SimpleCommandExceptionType(() -> "This warp is public, players already have access to this warp");
}