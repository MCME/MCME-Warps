package com.mcmiddleearth.warps.velocity.commands;

import com.google.common.collect.ImmutableMap;
import com.mcmiddleearth.warps.core.Utils;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpPredicates;
import com.mcmiddleearth.warps.velocity.commands.helpers.WarpSuggester;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.VelocityBrigadierMessage;
import com.velocitypowered.api.proxy.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ListCommand {
    private static final int MAX_SUGGESTIONS = 50;
    private static final int PAGE_SIZE = 10;

    private static final String defaultOrdering = "alphabetical";
    private static final String defaultVisibility = "public";

    private static final Set<String> orderings = Set.of(defaultOrdering, "createdAt", "visits");
    private static final Set<String> visibilities = Set.of(defaultVisibility, "private", "all");

    private static final Map<String, FlagInfo> flagInfos = ImmutableMap.of(
        "-c", new FlagInfo("creator"),
        "-n", new FlagInfo("warp name"),
        "-w", new FlagInfo("A specific world on a server"),
        "-s", new FlagInfo("server"),
        "-v", new FlagInfo("visibility", defaultVisibility),
        "-o", new FlagInfo("ordering", defaultOrdering)
    );

    private static final SimpleCommandExceptionType WORLD_SERVER_ERROR =
        new SimpleCommandExceptionType(() -> "You can't use both the server (s) and world (w) filter - the world filter applies its own server filter.");

    private static final DynamicCommandExceptionType INVALID_VISIBILITY_TYPE =
        new DynamicCommandExceptionType(visibility -> () -> "Invalid visibility type: " + visibility);

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("list")
                .requires(requirement)
                .executes(ListCommand::execute)
                .then(BrigadierCommand.requiredArgumentBuilder("query", StringArgumentType.greedyString())
                        .suggests(ListCommand.suggestWithFlags(flagInfos))
                        .executes(ListCommand::execute));
    }

    public static Map<String, String> parseFilters(String input) {
        Map<String, String> filters = new HashMap<>();
        String[] parts = input.trim().split("\\s+");

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].startsWith("-") && parts[i].length() == 2) {
                String flag = parts[i].substring(1);
                if (i + 1 < parts.length && !parts[i + 1].startsWith("-")) {
                    filters.put(flag, parts[i + 1]);
                    i++; // skip the value
                }
            }
        }
        return filters;
    }

    private static Comparator<Warp> getComparator(String order) {
        if (order == null) {
            order = defaultOrdering;
        }

        return switch (order.toLowerCase()) {
            case "createdat" -> Comparator.comparing(Warp::getCreatedAt);
            case "visits" -> Comparator.comparing(Warp::getVisits).reversed();
            default -> Comparator.comparing(Warp::getName);
        };
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();

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

        Map<String, String> filters = parseFilters(input);

        // Either a non-player, or the player can use the warp
        Predicate<Warp> customFilter = w -> !(source instanceof Player sender) || w.isUsable(sender);

        return createList("list", input, filters, pageNumber, source, customFilter);
    }

    public static int createList(String cmdName, String input, Map<String, String>filters, int pageNumber, CommandSource source, Predicate<Warp> customFilter) throws CommandSyntaxException {
        if (filters.containsKey("w") && filters.containsKey("s")) {
            throw WORLD_SERVER_ERROR.create();
        }
        if (filters.containsKey("v")) {
            String visibility = filters.get("v").toUpperCase();
            if (!visibility.equals("ALL")) {
                try {
                    Warp.Type.valueOf(visibility);
                } catch (IllegalArgumentException e) {
                    throw INVALID_VISIBILITY_TYPE.create(filters.get("v"));
                }
            }
        }

        Comparator<Warp> comparator = getComparator(filters.get("o"));
        Predicate<Warp> predicate = warp -> {
            if (!customFilter.test(warp)) return false;

            if (filters.containsKey("c") && !warp.getCreatorName().equalsIgnoreCase(filters.get("c"))) {
                return false;
            }
            if (filters.containsKey("n") && !Utils.normaliseString(warp.getName()).contains(filters.get("n").toLowerCase())) {
                return false;
            }
            if (filters.containsKey("s") && !warp.getServer().equalsIgnoreCase(filters.get("s"))) {
                return false;
            }
            if (filters.containsKey("w")) {
                String[] values = filters.get("w").split("/"); // world/server
                String world = values[0];
                String server = values[1];

                if (!warp.getLocation().world().equalsIgnoreCase(world) || !warp.getServer().equalsIgnoreCase(server)) {
                    return false;
                }
            }
            if (filters.containsKey("v")) {
                String visibility = filters.get("v").toUpperCase();

                // If "all" then do nothing
                if (!visibility.equals("ALL")) {
                    Warp.Type type = Warp.Type.valueOf(visibility);
                    return warp.isOfType(type);
                }
            }
            // Else use the default
            else return warp.isOfType(Warp.Type.valueOf(defaultVisibility.toUpperCase()));

            return true;
        };

        Map<String, Warp> matchingWarps = WarpManager.getWarps(predicate);
        List<Warp> warps = matchingWarps.values().stream()
            .sorted(comparator)
            .toList();

        if (warps.isEmpty()) {
            source.sendMessage(
                Component.text("No warps found matching the filters.", NamedTextColor.YELLOW)
            );
            return Command.SINGLE_SUCCESS;
        }

        int total = warps.size();
        int start = (pageNumber - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, total);

        if (start >= total) {
            source.sendMessage(
                Component.text("Page " + pageNumber + " does not exist.", NamedTextColor.RED)
            );
            return Command.SINGLE_SUCCESS;
        }

        // Construct the page component and send it to the sender
        TextComponent.Builder page = Component.text().appendNewline();

        // Send header with page info
        int totalPages = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        page.append(
            Component.text("Warps - page %s/%s (%s)".formatted(pageNumber, totalPages, total), NamedTextColor.GOLD)
        ).appendNewline();

        List<Warp> pageWarps = warps.subList(start, end);
        for (Warp warp : pageWarps) {
            String warpName = warp.getName();

            Component warpComponent = Component.text(" -", NamedTextColor.GRAY)
                .append(
                    Component.text("〘", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
                        .append(Component.text("w").decoration(TextDecoration.BOLD, false))
                        .append(Component.text("〙"))
                        .clickEvent(ClickEvent.runCommand("/warp " + warpName))
                ).append(
                    Component.text(warpName, NamedTextColor.AQUA)
                        .hoverEvent(
                            HoverEvent.showText(WarpSuggester.buildWarpTooltip(warp))
                        )
                );

            page.append(warpComponent).appendNewline();
        }

        // Pagination buttons
        Component prevButton = Component.text("[prev]");
        prevButton = pageNumber > 1
            ? prevButton
                .color(NamedTextColor.GOLD)
                .clickEvent(ClickEvent.runCommand("/warpmanager %s %s %s".formatted(cmdName, input, pageNumber - 1)))
            : prevButton
                .color(TextColor.color(0x5c5c5c))
                .decorate(TextDecoration.ITALIC);

        Component nextButton = Component.text("[next]");
        nextButton = pageNumber < totalPages
            ? nextButton
                .color(NamedTextColor.GOLD)
                .clickEvent(ClickEvent.runCommand("/warpmanager %s %s %s".formatted(cmdName, input, pageNumber + 1)))
            : nextButton
                .color(TextColor.color(0x5c5c5c))
                .decorate(TextDecoration.ITALIC);

        page.appendNewline()
            .append(prevButton)
            .appendSpace()
            .append(nextButton);

        source.sendMessage(page);
        return Command.SINGLE_SUCCESS;
    }

    public static SuggestionProvider<CommandSource> suggestWithFlags(Map<String, FlagInfo> flagInfos) {
        return (context, builder) -> suggest(context, builder, flagInfos);
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSource> context, SuggestionsBuilder builder, Map<String, FlagInfo> flagInfos) {
        if (!(context.getSource() instanceof Player sender)) {
            return Suggestions.empty();
        }

        String remaining = builder.getRemainingLowerCase();
        String[] parts = remaining.isEmpty() ? new String[0] : remaining.split("\\s+", -1);

        Set<String> usedFlags = new HashSet<>();
        boolean expectingValue = false;
        String lastFlag = null;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean isPartAFlag = flagInfos.containsKey(part);

            if (isPartAFlag) {
                usedFlags.add(part);

                // list -c
                // list -c dra
                // list -c drayz -w mor

                // If the next part is the final part or there is no next part
                if (i + 1 >= parts.length - 1) {
                    expectingValue = true;
                    lastFlag = part;
                    break;
                } else {
                    i++; // skip value
                }
            }
        }

        int lastSpace = remaining.lastIndexOf(' ');

        // currentArg == everything after the last space (otherwise it's just the whole input)
        String currentArg = (lastSpace != -1) ? remaining.substring(lastSpace + 1) : remaining;

        // Offset the suggestions to be after the last space
        int offset = builder.getStart() + (lastSpace != -1 ? lastSpace + 1 : 0);
        builder = builder.createOffset(offset);

        if (expectingValue) {
            Predicate<Warp> usable = WarpPredicates.usableBy(sender);

            if (currentArg.equals(lastFlag)) {
                return builder.buildFuture();
            }

            // suggest value for lastFlag
            switch (lastFlag) {
                case "-s" -> {
                    WarpManager.getWarps(usable).values().stream()
                        .map(Warp::getServer)
                        .filter(s -> s.toLowerCase().startsWith(currentArg))
                        .limit(MAX_SUGGESTIONS)
                        .collect(Collectors.toSet())
                        .forEach(builder::suggest);
                }
                case "-w" -> {
                    WarpManager.getWarps(usable).values().stream()
                        .map(warp -> warp.getLocation().world() + "/" + warp.getServer())
                        .filter(pair -> pair.toLowerCase().startsWith(currentArg))
                        .limit(MAX_SUGGESTIONS)
                        .collect(Collectors.toSet())
                        .forEach(builder::suggest);
                }
                case "-c" -> {
                    WarpManager.getWarps(usable).values().stream()
                        .map(Warp::getCreatorName)
                        .filter(c -> c.toLowerCase().startsWith(currentArg))
                        .limit(MAX_SUGGESTIONS)
                        .collect(Collectors.toSet())
                        .forEach(builder::suggest);
                }
                case "-n" -> {
                    WarpManager.getWarpNames(usable).values().stream()
                        .filter(n -> n.toLowerCase().contains(currentArg))
                        .limit(MAX_SUGGESTIONS)
                        .toList()
                        .forEach(builder::suggest);
                }
                case "-v" -> {
                    visibilities.stream()
                        .filter(v -> v.startsWith(currentArg))
                        .forEach(builder::suggest);
                }
                case "-o" -> {
                    orderings.stream()
                        .filter(o -> o.toLowerCase().startsWith(currentArg))
                        .forEach(builder::suggest);
                }
            }
        } else {
            // Suggest remaining flags
            for (var entry : flagInfos.entrySet()) {
                String flag = entry.getKey();
                FlagInfo info = entry.getValue();

                boolean isUnused = !usedFlags.contains(flag);
                if (isUnused && flag.contains(currentArg)) {
                    if (flag.equals("-w") && usedFlags.contains("-s")) {
                        continue;
                    } else if (flag.equals("-s") && usedFlags.contains("-w")) {
                        continue;
                    }

                    Component tooltip = Component.text(info.description);
                    if (info.defaultValue != null) {
                        tooltip = tooltip.append(Component.text(" (default: %s)".formatted(info.defaultValue), NamedTextColor.GRAY, TextDecoration.ITALIC));
                    }
                    builder.suggest(
                        flag,
                        VelocityBrigadierMessage.tooltip(tooltip)
                    );
                }
            }
        }

        return builder.buildFuture();
    }

    public record FlagInfo(String description, String defaultValue) {
        // Overloaded constructor for when there's no defaultValue
        public FlagInfo(String description) {
            this(description, null);
        }
    }
}