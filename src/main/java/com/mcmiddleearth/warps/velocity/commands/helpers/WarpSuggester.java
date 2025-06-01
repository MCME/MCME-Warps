package com.mcmiddleearth.warps.velocity.commands.helpers;

import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.*;

public class WarpSuggester {
    private static final Double DISTANCE_THRESHOLD = 0.4;
    private static final int FUZZY_SUGGESTIONS_LIMIT = 3;
    private static final JaroWinklerDistance distance = new JaroWinklerDistance();

    private final String input;
    private final List<String> warpNames;

    public WarpSuggester(List<String> warpNames, String rawInput) {
        this.input = normalise(rawInput);
        // Q: Create a map of warpName to normalisedWarpName?
        this.warpNames = warpNames;
    }

    // FIXME: Requires WarpManager to also not care about apostrophes
    // -> share a base normaliser?
    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT)
            .replace("'", "")
            // Collapse multiple spaces
            .replaceAll("\\s+", " ")
            // Can't use trim otherwise startsWith doesn't filter properly
            .stripLeading();
    }

    public List<String> getSuggestions() {
        if (input.isEmpty()) { return warpNames; }

        List<String> startsWithSuggestions = getStartsWithSuggestions();

        // Tab completion only replaces a single word,
        // therefore if the user has entered >1 word the
        // suggestions must not include *words* that the user has already typed
        // TODO: Suggestions v3 - fuzzy matching on the current word
        if (input.contains(" ")) {
            // Even if startsWithSuggestions is empty we must return here
            return getPartialSuggestions(input, startsWithSuggestions);
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
        return warpNames.stream().filter(warpName -> normalise(warpName).startsWith(input)).toList();
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
        return warpNames.stream().filter(warpName -> normalise(warpName).contains(input)).toList();
    }

    private record ScoredWarp(String name, double score) {}
    private List<String> getFuzzySuggestions() {
        List<String> suggestions = new ArrayList<>();

        PriorityQueue<ScoredWarp> topSuggestions = new PriorityQueue<>(
            Comparator.comparingDouble(ScoredWarp::score).reversed()
        );

        for (String warpName : warpNames) {
            String normalizedWarp = normalise(warpName);
            double score = distance.apply(normalizedWarp, input);

            // No point suggesting poor matches (lower is better)
            if (score > DISTANCE_THRESHOLD) continue;

            topSuggestions.offer(new ScoredWarp(warpName, score));
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
