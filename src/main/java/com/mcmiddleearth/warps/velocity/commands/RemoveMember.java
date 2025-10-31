package com.mcmiddleearth.warps.velocity.commands;

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
import java.util.function.Predicate;

public class RemoveMember {
    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("remove-member")
            .requires(requirement)
            .then(BrigadierCommand.requiredArgumentBuilder("warp", StringArgumentType.string())
                .suggests(RemoveMember::suggestWarpName)
                .then(BrigadierCommand.requiredArgumentBuilder("member", StringArgumentType.word())
                    .suggests(RemoveMember::suggestMembers)
                    .executes(RemoveMember::execute)
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
        final String playerName = context.getArgument("member", String.class);

        Warp warp = CommandUtils.getWarp( context, "warp").value();
        if (!warp.isOfType(Warp.Type.PRIVATE)) throw WARP_NOT_PRIVATE.create();
        if (!warp.isCreator(sender)) throw NOT_ALLOWED.create();

        Player targetPlayer = WarpVelocity.getInstance().getProxy()
            .getPlayer(playerName)
            .orElseThrow(() -> INVALID_PLAYER.create(playerName));

        Set<UUID> memberIDs = warp.getMembers();
        if (!memberIDs.contains(targetPlayer.getUniqueId())) throw NOT_MEMBER_EXCEPTION.create();

        warp.removeMember(targetPlayer.getUniqueId());
        try {
            WarpManager.saveWarp(warp);
            sender.sendRichMessage("<green>" + targetPlayer.getUsername() + " has been removed from warp " + warpName);
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

        WarpSuggester.suggest(builder, warp -> warp.isOfType(Warp.Type.PRIVATE) && warp.isCreator(sender), true);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMembers(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String warpName = context.getArgument("warp", String.class);
        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) return Suggestions.empty();

        String input = builder.getRemainingLowerCase();

        Set<UUID> memberIDs = warp.getMembers();
        memberIDs.forEach(id -> WarpVelocity.getInstance().getProxy().getPlayer(id).ifPresent(onlineMember -> {
            String name = onlineMember.getUsername();

            if (name.toLowerCase().startsWith(input)) {
                builder.suggest(name);
            }
        }));

        return builder.buildFuture();
    }

    private static final DynamicCommandExceptionType INVALID_PLAYER =
        new DynamicCommandExceptionType(name -> new LiteralMessage("Unable to find an *online* player with the name: " + name));

    private static final SimpleCommandExceptionType NOT_ALLOWED =
        new SimpleCommandExceptionType(() -> "Only the creator of a warp can add/remove members");

    private static final SimpleCommandExceptionType NOT_MEMBER_EXCEPTION =
        new SimpleCommandExceptionType(() -> "That player is not a member of this warp");

    private static final SimpleCommandExceptionType WARP_NOT_PRIVATE =
        new SimpleCommandExceptionType(() -> "This warp isn't private, unable to perform action");
}