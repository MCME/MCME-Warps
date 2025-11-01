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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class Members {
    private enum Operation { ADD, REMOVE }

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("members")
            .requires(requirement)
            .then(BrigadierCommand.literalArgumentBuilder("add")
                .then(BrigadierCommand.requiredArgumentBuilder("private-warp", StringArgumentType.string())
                    .suggests(Members::suggestPrivateWarps)
                    .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests(Members::suggestPlayers)
                        .executes(context -> execute(context, Operation.ADD))
                    )
                )
            )
            .then(BrigadierCommand.literalArgumentBuilder("remove")
                .then(BrigadierCommand.requiredArgumentBuilder("private-warp", StringArgumentType.string())
                    .suggests(Members::suggestPrivateWarps)
                    .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests(Members::suggestMembers)
                        .executes(context -> execute(context, Operation.REMOVE))
                    )
                )
            );
    }

    private static int execute(CommandContext<CommandSource> context, Operation operation) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        final String playerName = context.getArgument("player", String.class);

        Warp warp = CommandUtils.getWarp( context, "private-warp").value();
        if (!warp.isOfType(Warp.Type.PRIVATE)) throw WARP_NOT_PRIVATE.create();
        if (!warp.isCreator(sender)) throw NOT_ALLOWED.create();

        // Currently targetPlayer has to be an online player!
        Player targetPlayer = WarpVelocity.getInstance().getProxy()
            .getPlayer(playerName)
            .orElseThrow(() -> INVALID_PLAYER.create(playerName));

        if (operation.equals(Operation.ADD)) return addMember(warp, targetPlayer, sender);
        return removeMember(warp, targetPlayer, sender);
    }

    private static int addMember(Warp warp, Player targetPlayer, Player sender) throws CommandSyntaxException {
        if (warp.isCreator(targetPlayer)) {
            throw IS_CREATOR.create();
        }

        Set<UUID> memberIDs = warp.getMembers();
        if (memberIDs.contains(targetPlayer.getUniqueId())) {
            throw ALREADY_MEMBER.create();
        }

        warp.addMember(targetPlayer.getUniqueId());
        try {
            WarpManager.saveWarp(warp);
            sender.sendRichMessage("<green>" + targetPlayer.getUsername() + " has been added to warp " + warp.getName());
            targetPlayer.sendRichMessage("<green>You have been added to warp " + warp.getName());
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            sender.sendRichMessage("<red>" + e.getMessage());
            return 0;
        }
    }

    private static int removeMember(Warp warp, Player targetPlayer, Player sender) throws CommandSyntaxException {
        Set<UUID> memberIDs = warp.getMembers();
        if (!memberIDs.contains(targetPlayer.getUniqueId())) throw NOT_MEMBER_EXCEPTION.create();

        warp.removeMember(targetPlayer.getUniqueId());
        try {
            WarpManager.saveWarp(warp);
            sender.sendRichMessage("<green>" + targetPlayer.getUsername() + " has been removed from warp " + warp.getName());
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            sender.sendRichMessage("<red>" + e.getMessage());
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestPrivateWarps(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        WarpSuggester.suggest(builder, warp -> warp.isOfType(Warp.Type.PRIVATE) && warp.isCreator(sender), true);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
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

    private static CompletableFuture<Suggestions> suggestMembers(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String warpName = context.getArgument("private-warp", String.class);
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
        new DynamicCommandExceptionType(name -> new LiteralMessage("Unable to find an online player with name '" + name + "'"));

    private static final SimpleCommandExceptionType NOT_ALLOWED =
        new SimpleCommandExceptionType(() -> "Only the creator of a warp can add/remove members");

    private static final SimpleCommandExceptionType ALREADY_MEMBER =
        new SimpleCommandExceptionType(() -> "This player is already a member of the warp!");

    private static final SimpleCommandExceptionType NOT_MEMBER_EXCEPTION =
        new SimpleCommandExceptionType(() -> "That player is not a member of this warp");

    private static final SimpleCommandExceptionType IS_CREATOR =
        new SimpleCommandExceptionType(() -> "You can't add the creator");

    private static final SimpleCommandExceptionType WARP_NOT_PRIVATE =
        new SimpleCommandExceptionType(() -> "This warp isn't private, unable to perform action");
}