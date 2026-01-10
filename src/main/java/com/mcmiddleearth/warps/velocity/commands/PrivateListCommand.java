package com.mcmiddleearth.warps.velocity.commands;

import com.google.common.collect.ImmutableMap;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

import java.util.*;
import java.util.function.Predicate;

public class PrivateListCommand {
    private static final Map<String, ListCommand.FlagInfo> flagInfos = ImmutableMap.of(
        "-n", new ListCommand.FlagInfo("warp name"),
        "-w", new ListCommand.FlagInfo("A specific world on a server"),
        "-s", new ListCommand.FlagInfo("server"),
        "-o", new ListCommand.FlagInfo("ordering", "alphabetical")
    );

    private static final SimpleCommandExceptionType V_FLAG_ERROR =
        new SimpleCommandExceptionType(() -> "You can't use the -v visibility filter, plist always uses 'private'. Use the 'list' command instead.");

    private static final SimpleCommandExceptionType C_FLAG_ERROR =
        new SimpleCommandExceptionType(() -> "You can't use the -c creator filter, plist only shows private warps created by you (or warps you are a member of). Use the 'list' command instead.");

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("plist")
                .requires(requirement)
                .executes(PrivateListCommand::execute)
                .then(BrigadierCommand.requiredArgumentBuilder("query", StringArgumentType.greedyString())
                        .suggests(ListCommand.suggestWithFlags(flagInfos))
                        .executes(PrivateListCommand::execute));
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            return Command.SINGLE_SUCCESS;
        }

        String input = "";
        try {
            input = context.getArgument("query", String.class);
        } catch (IllegalArgumentException e) {
            // The argument is optional so just continue
        }

        // Extract (and remove) the page number from the end of the input
        int pageNumber = 1;
        String trimmedInput = input.trim();
        String[] parts = trimmedInput.split("\\s+");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            try {
                // Throws if lastPart is not a number
                // Page is 1-indexed and prevented from being smaller
                pageNumber = Math.max(1, Integer.parseInt(lastPart));

                // Remove the page part from input
                int lastIndex = trimmedInput.lastIndexOf(lastPart);
                input = trimmedInput.substring(0, lastIndex).trim();
            } catch (NumberFormatException e) {
                // No page, keep input as is
            }
        }

        Map<String, String> filters = ListCommand.parseFilters(input);
        if (filters.containsKey("v")) {
            throw V_FLAG_ERROR.create();
        }
        if (filters.containsKey("c")) {
            throw C_FLAG_ERROR.create();
        }

        // This has to be added to filters (instead of being in the custom filter) otherwise ListCommand adds a default!
        filters.put("v", "private");

        // Player must either be the creator or a member
        Predicate<Warp> customFilter = w -> {
            return w.isCreator(player) || w.getMembers().contains(player.getUniqueId());
        };

        return ListCommand.createList("plist", input, filters, pageNumber, source, customFilter);
    }
}