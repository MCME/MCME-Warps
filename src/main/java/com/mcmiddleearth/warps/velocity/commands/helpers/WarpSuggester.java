package com.mcmiddleearth.warps.velocity.commands.helpers;

import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.*;

public class WarpSuggester {
    private static final Double DISTANCE_THRESHOLD = 0.4;
    private static final int FUZZY_SUGGESTIONS_LIMIT = 3;
    private static final JaroWinklerDistance distance = new JaroWinklerDistance();

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

    private record ScoredWarp(String name, double score) {}

    public static List<String> getSuggestions(List<String> warpNames, String rawInput) {
        String input = normalise(rawInput);

        List<String> suggestions = new ArrayList<>();
        warpNames.stream().filter(warpName -> normalise(warpName).startsWith(input)).forEach(suggestions::add);

        if (!suggestions.isEmpty()) {
            // There's at least 1 exact match - no need for fuzzy matching
            return suggestions;
        }

        // TODO: Either add .contains() suggestions OR use fzf style fuzzy for matching sub-words (e.g. deep for Helm's Deep)

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
