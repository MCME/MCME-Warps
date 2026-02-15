package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.core.Utils;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.VelocityBrigadierMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class WarpSuggester {
    private static final Double DISTANCE_THRESHOLD = 0.2;
    private static final int FUZZY_SUGGESTIONS_LIMIT = 3;
    private static final int MAX_SUGGESTIONS = 50;
    private static final JaroWinklerDistance distance = new JaroWinklerDistance();
    private static final NumberFormat compact = NumberFormat.getCompactNumberInstance();

    private static boolean shouldAddQuotes;

    private static String normaliseInput(String input) {
        // Can't use trim, since trailing whitespace is used to determine
        // when the user has started to enter a new word
        String temp = Utils.normaliseString(input).stripLeading();

        // Some commands use StringArgument.String, which requires quotes for typing >1 word
        // So stripping quotes allows the suggestions to still correctly match
        String unquoted = StringUtils.strip(temp, "\"'");
        return unquoted;
    }

    private static String formatRelative(String createdAt) {
        Duration d = Duration.between(Instant.parse(createdAt), Instant.now());

        long years = d.toDays() / 365;
        if (years > 0) return years + "y ago";

        long months = d.toDays() / 30;
        if (months > 0) return months + "mo ago";

        long days = d.toDays();
        if (days > 0) return days + "d ago";

        long hours = d.toHours();
        if (hours > 0) return hours + "h ago";

        long minutes = d.toMinutes();
        if (minutes > 0) return minutes + "m ago";

        return "just now";
    }

    public static Component buildWarpTooltip(Warp warp) {
        Component divider = Component.text(" | ", NamedTextColor.BLUE);
        TextComponent.Builder tooltip = Component.text().content("");

        int visits = warp.getVisits();
        tooltip.append(Component.text(compact.format(visits) + " visits"));
        tooltip.append(divider);

        String warpWorld = warp.getLocation().world();
        tooltip.append(Component.text(warpWorld));

        // Only show the server if it is different to the world
        String warpServer = warp.getServer();
        boolean showServer = !warpServer.equalsIgnoreCase(warpWorld);
        if (showServer) tooltip.append(Component.text(" (" + warpServer + ")"));
        tooltip.append(divider);

        tooltip.append(Component.text(warp.getCreatorName()));

        String relative = formatRelative(warp.getCreatedAt());
        tooltip.append(Component.text(" " + relative));

        if (warp.isOfType(Warp.Type.PRIVATE)) {
            tooltip.append(Component.text(" 🔒", NamedTextColor.RED));
        }

        return tooltip.build();
    }

    private static void buildSuggestions(SuggestionsBuilder builder, Collection<Warp> warps) {
        for (Warp w : warps) {
            String name = shouldAddQuotes ? "\""+w.getName()+"\"" : w.getName();
            Message tooltip = VelocityBrigadierMessage.tooltip(buildWarpTooltip(w));
            builder.suggest(name, tooltip);
        }
    }

    public static void suggest(SuggestionsBuilder builder, Predicate<Warp> filter) {
        suggest(builder, filter, false);
    }
    public static void suggest(SuggestionsBuilder builder, Predicate<Warp> filter, boolean shouldAddQuotes) {
        WarpSuggester.shouldAddQuotes = shouldAddQuotes;
        var warps = WarpManager.getWarps(filter);

        String rawInput = builder.getRemainingLowerCase();
        if (rawInput.isEmpty()) {
            // empty input -> suggest all warps
            List<Warp> suggestions = warps.values().stream().limit(MAX_SUGGESTIONS).toList();
            buildSuggestions(builder, suggestions);
            return;
        }
        String cleansedInput = normaliseInput(rawInput);

        // "hello"       -> ""      & "hello"
        // "hello world" -> "hello" & "world"
        int idx = cleansedInput.lastIndexOf(' ');
        String completedWords = (idx == -1) ? ""            : cleansedInput.substring(0, idx);
        String currentWord    = (idx == -1) ? cleansedInput : cleansedInput.substring(idx + 1);

        // Narrow the search to warps starting with completedWords
        Integer wordIndex = StringUtils.countMatches(cleansedInput, " ");
        var warpsSubset = warps.entrySet()
            .stream()
            .filter(warpEntry -> warpEntry.getKey().startsWith(completedWords))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // * Is 'contains' helpful? Replace with startsWith split by space? Or remove entirely?
        // * Explore other fuzzy matchers, like fzf
        List<Supplier<Map<String, Warp>>> strategies = List.of(
            () -> getStartsWithSuggestions(currentWord, wordIndex, warpsSubset),
            () -> getContainsSuggestions(currentWord, wordIndex, warpsSubset),
            () -> getFuzzySuggestions(cleansedInput, warpsSubset),
            () -> getFuzzySuggestions(cleansedInput, warps)
        );

        // Perform the matching strategies in order, exiting early if we get any suggestions
        Collection<Warp> suggestions = strategies.stream()
            .map(Supplier::get)
            .filter(m -> !m.isEmpty())
            .findFirst()
            .orElse(Map.of())
            .values();

        buildSuggestions(builder, suggestions);
    }

    private static Map<String, Warp> getStartsWithSuggestions(String currentWord, Integer wordIdx,  Map<String, Warp> warps) {
        return warps.entrySet()
            .stream()
            .filter(entry -> {
                String normalisedWarpName = entry.getKey();
                var split = normalisedWarpName.split(" ", wordIdx + 1);
                if (wordIdx > split.length - 1) {
                    return false;
                }
                return split[wordIdx].startsWith(currentWord);
            })
            .limit(MAX_SUGGESTIONS)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, Warp> getContainsSuggestions(String currentWord, Integer wordIdx, Map<String, Warp> warps) {
        return warps.entrySet()
            .stream()
            .filter(entry -> {
                String normalisedWarpName = entry.getKey();
                var split = normalisedWarpName.split(" ", wordIdx + 1);
                if (wordIdx > split.length - 1) {
                    return false;
                }
                return split[wordIdx].contains(currentWord);
            })
            .limit(MAX_SUGGESTIONS)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private record ScoredWarp(String name, double score, Warp warp) {}
    private static Map<String, Warp> getFuzzySuggestions(String input, Map<String, Warp> warps) {
        PriorityQueue<ScoredWarp> topSuggestions = new PriorityQueue<>(
            Comparator.comparingDouble(ScoredWarp::score).reversed()
        );

        for (var entry : warps.entrySet()) {
            String normalizedWarpName = entry.getKey();
            double score = distance.apply(normalizedWarpName,input);

            // No point suggesting poor matches (lower is better)
            if (score > DISTANCE_THRESHOLD) continue;

            topSuggestions.offer(new ScoredWarp(entry.getKey(), score, entry.getValue()));
            if (topSuggestions.size() > FUZZY_SUGGESTIONS_LIMIT) {
                topSuggestions.poll(); // Remove the lowest score
            }
        }

        return topSuggestions
            .stream()
            .collect(Collectors.toMap(ScoredWarp::name, ScoredWarp::warp));
    }
}