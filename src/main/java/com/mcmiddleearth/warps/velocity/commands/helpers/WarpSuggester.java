package com.mcmiddleearth.warps.velocity.commands.helpers;

import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// TODO:
// * tab completion can trigger in the middle of the greedy string
//   * Fixed by manually setting the range?

// Q: How to handle prefixes?
// Q: How to do actual fuzzy matching (matching on a subword)

public class WarpSuggester {
    private static final Double DISTANCE_THRESHOLD = 0.25;
    private static final JaroWinklerDistance distance = new JaroWinklerDistance();

    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", "")   // keep letters, digits, and spaces
            .replaceAll("\\s+", " ")        // collapse multiple spaces
            .trim();

//        return Normalizer.normalize(s, Normalizer.Form.NFD)
//            .replaceAll("\\p{M}", "") // Remove diacritics
//            .toLowerCase(Locale.ROOT)
//            .trim();
    }

    public static List<String> getSuggestions(List<String> warpNames, String rawInput) {
        String input = normalise(rawInput);

        List<String> suggestions = new ArrayList<>();
        warpNames.stream().filter(warpName -> normalise(warpName).startsWith(input)).forEach(suggestions::add);

        // Q: Should this be 1????
        if (suggestions.size() >= 5) {
            // There's enough exact matches, no need for fuzzy matching
            return suggestions;
        }

        // Supplement suggestions with (at most) the top 5 suggestions
        warpNames.stream()
            .filter(suggestions::contains)
            // Perform string-similarity
            .map(warpName -> Map.entry(
                warpName,
                distance.apply(normalise(warpName), input)
            ))
            .filter(entry -> entry.getValue() < DISTANCE_THRESHOLD)
            // Only include the best suggestions
            .sorted(Map.Entry.comparingByValue())
            .limit(5 - suggestions.size()) // FIXME: Magic number, extract out of chain
            .forEach(entry -> suggestions.add(entry.getKey()));

        return suggestions;
    }
}
