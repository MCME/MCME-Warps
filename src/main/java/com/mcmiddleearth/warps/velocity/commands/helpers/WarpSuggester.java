package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.velocity.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.*;

public class WarpSuggester {
    private static final Double DISTANCE_THRESHOLD = 0.4;
    private static final int FUZZY_SUGGESTIONS_LIMIT = 3;
    private static final JaroWinklerDistance distance = new JaroWinklerDistance();

    private final String rawInput;
    private final String cleanedInput;
    private final Map<String, String> warpNames;

    public WarpSuggester(Map<String, String> warpNames, String rawInput) {
        this.rawInput = rawInput;
        this.cleanedInput = normaliseInput(rawInput);

        this.warpNames = warpNames;
    }

    private static String normaliseInput(String input) {
        // Can't use trim, since trailing whitespace is used by startsWith
        // to determine when the user has started to enter a new word
        String temp = Utils.normaliseString(input).stripLeading();

        // Some commands use StringArgument.Word, which requires quotes for typing >1 word
        // So stripping quotes allows the suggestions to still correctly match
        // (quotes are then added later)
        String unquoted = StringUtils.strip(temp, "\"'");
        return unquoted;
    }

    public Collection<String> getSuggestions() {
        if (rawInput.isEmpty()) { return warpNames.values(); }

        List<String> startsWithSuggestions = getStartsWithSuggestions();

        // Tab completion only replaces a single word,
        // therefore if the user has entered >1 word the
        // suggestions must not include *words* that the user has already typed
        // TODO: Suggestions v3 - fuzzy matching on the current word
        if (cleanedInput.contains(" ")) {
            // Even if startsWithSuggestions is empty we must return here
            return getPartialSuggestions(cleanedInput, startsWithSuggestions);
        }

        if (!startsWithSuggestions.isEmpty()) {
            // There's at least 1 exact match - no need to attempt looser matching
            return startsWithSuggestions;
        }

        // Q: Would it be better just to use fuzzy? Or replace this with a startsWith split by space?
        List<String> containsSuggestions = getContainsSuggestions();
        if (!containsSuggestions.isEmpty()) {
            return containsSuggestions;
        }

        // TODO: Explore other fuzzy matchers, like fzf
        return getFuzzySuggestions();
    }

    private List<String> getStartsWithSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (var entry : warpNames.entrySet()) {
            // Using the normalised warp name for the comparison
            if (entry.getKey().startsWith(cleanedInput)) {
                // Suggesting the un-normalised warp name
                suggestions.add(entry.getValue());
            }
        }
        return suggestions;
    }

    private List<String> getPartialSuggestions(String input, List<String> startsWithSuggestions) {
        List<String> updatedSuggestions = new ArrayList<>();

        String[] inputWords = input.split(" ");
        int inputWordsCount = inputWords.length;
        if (input.endsWith(" ")) {
            // split() doesn't include trailing empty strings, so account for that here
            // e.g. /warp amon ^
            inputWordsCount += 1;
        }

        for (String suggestion : startsWithSuggestions) {
            String[] suggestionWords = suggestion.split(" ");

            int currWordIndex = inputWordsCount - 1;
            if (currWordIndex < suggestionWords.length) {
                String remaining = String.join(" ", Arrays.copyOfRange(suggestionWords, currWordIndex, suggestionWords.length));
                updatedSuggestions.add(remaining);
            }
        }

        // warp amon h^ -> hen
        // warp amon ^ -> hen
        return updatedSuggestions;
    }

    private List<String> getContainsSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (var entry : warpNames.entrySet()) {
            // Using the normalised warp name for the comparison
            if (entry.getKey().contains(cleanedInput)) {
                // Suggesting the un-normalised warp name
                suggestions.add(entry.getValue());
            }
        }
        return suggestions;
    }

    private record ScoredWarp(String name, double score) {}
    private List<String> getFuzzySuggestions() {
        List<String> suggestions = new ArrayList<>();

        PriorityQueue<ScoredWarp> topSuggestions = new PriorityQueue<>(
            Comparator.comparingDouble(ScoredWarp::score).reversed()
        );

        for (var entry : warpNames.entrySet()) {
            String normalizedWarpName = entry.getKey();
            double score = distance.apply(normalizedWarpName, cleanedInput);

            // No point suggesting poor matches (lower is better)
            if (score > DISTANCE_THRESHOLD) continue;

            topSuggestions.offer(new ScoredWarp(entry.getValue(), score));
            if (topSuggestions.size() > FUZZY_SUGGESTIONS_LIMIT) {
                topSuggestions.poll(); // Remove the lowest score
            }
        }

        // Return the warp names - order doesn't matter (sorted alphabetically client side)
        for (ScoredWarp scoredWarp: topSuggestions) {
            suggestions.add(scoredWarp.name());
        }

        return suggestions;
    }
}
