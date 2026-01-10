package com.mcmiddleearth.warps.velocity.commands;

import com.google.common.collect.ImmutableMap;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class Assets {
    private static final Map<String, ListCommand.FlagInfo> flagInfos = ImmutableMap.of(
        "-n", new ListCommand.FlagInfo("warp name"),
        "-o", new ListCommand.FlagInfo("ordering", "alphabetical")
    );

    private static final SimpleCommandExceptionType FLAG_NOT_ALLOWED =
        new SimpleCommandExceptionType(() -> "You can't use the 'v', 'c', 's', or 'w' flags with the assets command. Use the 'list' command instead.");

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("assets")
                .requires(requirement)
                .executes(Assets::execute)
                .then(BrigadierCommand.requiredArgumentBuilder("query", StringArgumentType.greedyString())
                        .suggests(ListCommand.suggestWithFlags(flagInfos))
                        .executes(Assets::execute));
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
        if (filters.containsKey("v") || filters.containsKey("c") || filters.containsKey("w") || filters.containsKey("s")) {
            throw FLAG_NOT_ALLOWED.create();
        }

        Optional<ServerConnection> connection = player.getCurrentServer();
        if (connection.isEmpty()) return Command.SINGLE_SUCCESS;

        filters.put("v", "private");
        filters.put("c", player.getUsername());
        filters.put("s", connection.get().getServerInfo().getName());

        return ListCommand.createList("assets", input, filters, pageNumber, source, w -> true);
    }
}