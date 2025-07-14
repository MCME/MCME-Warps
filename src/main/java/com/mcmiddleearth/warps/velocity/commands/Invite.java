package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.velocity.Permission;
import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.mcmiddleearth.warps.velocity.commands.helpers.CommandUtils;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Invite {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("invite")
            .requires(sender -> sender.hasPermission(Permission.INVITE.getNode()))
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

        Warp warp = CommandUtils.getWarp( context, "warp").value();
        if (!warp.isOfType(Warp.Type.PRIVATE)) throw WARP_NOT_PRIVATE.create();
        if (!warp.isCreator(sender)) throw NOT_ALLOWED.create();

        Player targetPlayer = WarpVelocity.getInstance().getProxy()
            .getPlayer(playerName)
            .orElseThrow(() -> INVALID_PLAYER.create(playerName));

        if (warp.isCreator(targetPlayer)) {
            throw ALREADY_MEMBER.create();
        }

        Set<UUID> memberIDs = warp.getMembers().keySet();
        if (memberIDs.contains(targetPlayer.getUniqueId())) {
            throw ALREADY_MEMBER.create();
        }

        warp.addPlayer(targetPlayer);
        try {
            WarpManager.saveWarp(warp);
            sender.sendRichMessage("<green>" + targetPlayer.getUsername() + " has been added to warp " + warpName);
            targetPlayer.sendRichMessage("<green>You have been added to warp " + warpName);
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

        String input = builder.getRemainingLowerCase();
        Map<String, String> warpNames = WarpManager.getWarpNames(warp -> warp.isOfType(Warp.Type.PRIVATE) && warp.isCreator(sender));
        var warpSuggester = new WarpSuggester(warpNames, input);
        Collection<String> suggestions = warpSuggester.getSuggestions();

        // warp can't be a greedy arg, so wrap suggestions in quotes
        suggestions.forEach(suggestion -> builder.suggest("\"" + suggestion + "\""));
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
        new SimpleCommandExceptionType(() -> "Only the creator of a warp can invite/uninvite players");

    private static final SimpleCommandExceptionType ALREADY_MEMBER =
        new SimpleCommandExceptionType(() -> "This player is already a member of the warp!");

    private static final SimpleCommandExceptionType WARP_NOT_PRIVATE =
        new SimpleCommandExceptionType(() -> "This warp isn't private, unable to perform action");
}