package com.mcmiddleearth.warps.velocity.commands.helpers;

import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.*;

public class WarpSuggester {
    private static final Double DISTANCE_THRESHOLD = 0.4;
    private static final int FUZZY_SUGGESTIONS_LIMIT = 3;
    private static final JaroWinklerDistance distance = new JaroWinklerDistance();

    private final String rawInput;
    private final String input;
    private final List<String> warpNames;

    public WarpSuggester(List<String> warpNames, String rawInput) {
        this.rawInput = rawInput;
        this.input = normalise(rawInput);
        // Q: Create a map of warpName to normalisedWarpName?
        this.warpNames = warpNames;
    }

    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", "") // keep letters, digits, and spaces
            .replaceAll("\\s+", " ")      // collapse multiple spaces
            .trim();

//        return Normalizer.normalize(s, Normalizer.Form.NFD)
//            .replaceAll("\\p{M}", "") // Remove diacritics
//            .toLowerCase(Locale.ROOT)
//            .trim();
    }

    public List<String> getSuggestions() {
        if (rawInput.isEmpty()) { return warpNames; }

        List<String> startsWithSuggestions = getStartsWithSuggestions();

        // Tab completion only replaces a single word
        // Therefore if the user has entered >1 word the suggestion needs to not include
        // what the user has already typed
        if (rawInput.contains(" ")) { return getPartialSuggestions(rawInput, startsWithSuggestions); }

        if (!startsWithSuggestions.isEmpty()) {
            // There's at least 1 exact match - no need to attempt looser matching
            return startsWithSuggestions;
        }

        // Q: Would it be better just to use fuzzy? Remove? Or replace with a startsWith split by space?
        List<String> containsSuggestions = getContainsSuggestions();
        if (!containsSuggestions.isEmpty()) {
            return containsSuggestions;
        }

        return getFuzzySuggestions();
    }

    private List<String> getStartsWithSuggestions() {
        return warpNames.stream().filter(warpName -> normalise(warpName).startsWith(input)).toList();
    }

    private List<String> getPartialSuggestions(String rawInput, List<String> suggestions) {
        // helm's<space> -> helm's deep
        // helm's d -> helm's deep
        // helm<space> -> x

        // helms -> helm's deep
        // FIXME: Normalise inside getWarp and this will be fine!
        // helms<space> -> x

        List<String> updatedSuggestions = new ArrayList<>();

        // trim() to remove leading spaces, split wherever there is 1 or more space
        String[] inputWords = rawInput.trim().split("\\s+");
        int inputWordsCount = inputWords.length;
        if (rawInput.endsWith(" ")) {
            // split() doesn't include trailing empty strings, so account for that here
            // e.g. /warp helm<space>
            inputWordsCount += 1;
        }

        for (String suggestion : suggestions) {
            String[] suggestionWords = suggestion.trim().split("\\s+");

            // e.g. /warp amon hen<space>
            if (inputWordsCount > suggestionWords.length) {
                continue;
            }

            // comparing input (paths of th: 3) to suggestion (paths of the dead)
            // comparing input (paths of<space>: 3) to suggestion (paths of the dead)
            if (!isPartialWordMatch(inputWords, suggestionWords, inputWordsCount)) {
                continue;
            }

            int firstIncompleteIndex = inputWordsCount - 1;
            if (firstIncompleteIndex < suggestionWords.length) {
                String remaining = String.join(" ", Arrays.copyOfRange(suggestionWords, firstIncompleteIndex, suggestionWords.length));
                updatedSuggestions.add(remaining);
            }
        }

        return updatedSuggestions;
    }

    // Determining if everything up till the current word is an exact match
    // FIXME: Shouldn't be needed with correct startsWith logic
    private static boolean isPartialWordMatch(String[] inputWords, String[] suggestionWords, int inputWordsCount) {
        for (int i = 0; i < inputWordsCount - 1; i++) {
            if (!suggestionWords[i].equalsIgnoreCase(inputWords[i])) {
                return false;
            }
        }
        return true;
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
