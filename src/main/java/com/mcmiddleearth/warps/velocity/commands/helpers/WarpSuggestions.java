package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.core.Utils;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpLookup;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Picks the warps to offer while a player types a warp name. Pure (no Velocity, no Brigadier), so
 * the rules can be unit tested; {@link WarpSuggester} turns the result into Brigadier suggestions.
 * <p>
 * Three rules keep the list useful:
 * <ul>
 *   <li>A plain name only offers public warps, the player's own, and private warps shared with them.
 *       The rest of other players' private warps (which staff may use) are offered once the player
 *       types {@code owner:} - otherwise thousands of them bury the public warps.</li>
 *   <li>When more warps match than fit, the player's own and the most visited ones are kept.</li>
 *   <li>Every text offered is an address {@link WarpLookup} leads back to that same warp.</li>
 * </ul>
 */
public final class WarpSuggestions {
    static final int MAX_SUGGESTIONS = 50;
    private static final double DISTANCE_THRESHOLD = 0.2;
    private static final int FUZZY_SUGGESTIONS_LIMIT = 3;
    private static final JaroWinklerDistance distance = new JaroWinklerDistance();

    private WarpSuggestions() {}

    /** A warp to offer, and the text that completes the player's input to reach it. */
    public record Entry(String text, Warp warp) {}

    /** {@code key} is the normalised warp name the input is matched against. */
    private record Candidate(String key, String text, Warp warp) {
        Entry entry() { return new Entry(text, warp); }
    }

    /**
     * @param viewer the player typing, or null for the console
     * @param filter what the command accepts (warps the player may use, may modify, public ones, ...)
     */
    public static List<Entry> select(Collection<Warp> warps, @Nullable UUID viewer, Predicate<Warp> filter, String rawInput) {
        String input = normaliseInput(rawInput);

        // "alice:ho" -> Alice's private warps, searched for "ho"
        int separator = input.indexOf(WarpLookup.OWNER_SEPARATOR);
        boolean hasOwner = separator > 0;
        String search = hasOwner ? input.substring(separator + 1).stripLeading() : input;
        List<Candidate> pool = hasOwner
            ? privateWarpsOf(input.substring(0, separator).strip(), warps, filter)
            : warpsWithinReach(warps, viewer, filter);
        pool.sort(mostLikelyFirst(viewer));

        if (search.isEmpty()) {
            return pool.stream().limit(MAX_SUGGESTIONS).map(Candidate::entry).toList();
        }

        // "hello"       -> ""      & "hello"
        // "hello world" -> "hello" & "world"
        int idx = search.lastIndexOf(' ');
        String completedWords = (idx == -1) ? ""     : search.substring(0, idx);
        String currentWord    = (idx == -1) ? search : search.substring(idx + 1);

        // Narrow the search to warps starting with completedWords
        int wordIndex = StringUtils.countMatches(search, " ");
        List<Candidate> subset = pool.stream().filter(c -> c.key().startsWith(completedWords)).toList();

        // Perform the matching strategies in order, stopping at the first that finds anything
        List<Supplier<List<Candidate>>> strategies = List.of(
            () -> wordMatches(subset, wordIndex, word -> word.startsWith(currentWord)),
            () -> wordMatches(subset, wordIndex, word -> word.contains(currentWord)),
            () -> closeSpellings(search, subset),
            () -> closeSpellings(search, pool)
        );
        return strategies.stream()
            .map(Supplier::get)
            .filter(found -> !found.isEmpty())
            .findFirst()
            .orElse(List.of())
            .stream().map(Candidate::entry).toList();
    }

    private static String normaliseInput(String input) {
        // Can't use trim, since trailing whitespace is used to determine
        // when the user has started to enter a new word
        String normalised = Utils.normaliseString(input).stripLeading();

        // Some commands use StringArgument.String, which requires quotes for typing >1 word
        // So stripping quotes allows the suggestions to still correctly match
        return StringUtils.strip(normalised, "\"'");
    }

    /** Public warps, the viewer's own, and private warps shared with them - each under its address. */
    private static List<Candidate> warpsWithinReach(Collection<Warp> warps, @Nullable UUID viewer, Predicate<Warp> filter) {
        Set<String> ownNames = new HashSet<>();
        for (Warp warp : warps) {
            if (isPrivateWarpOf(viewer, warp)) ownNames.add(key(warp));
        }

        List<Candidate> pool = new ArrayList<>();
        for (Warp warp : warps) {
            boolean isPublic = warp.isOfType(Warp.Type.PUBLIC);
            boolean sharedWithViewer = !isPublic && viewer != null && warp.getMembers().contains(viewer);
            if (!isPublic && !sharedWithViewer && !isPrivateWarpOf(viewer, warp)) continue;

            // The viewer's own private warp is what that name reaches, so the public one would be a
            // second, identical-looking entry that leads to the same place.
            if (isPublic && ownNames.contains(key(warp))) continue;

            if (filter.test(warp)) {
                pool.add(new Candidate(key(warp), WarpLookup.address(warp, viewer), warp));
            }
        }
        return pool;
    }

    /** One player's private warps, always written owner:name - also when the viewer names themselves. */
    private static List<Candidate> privateWarpsOf(String owner, Collection<Warp> warps, Predicate<Warp> filter) {
        List<Candidate> pool = new ArrayList<>();
        for (Warp warp : warps) {
            if (warp.isOfType(Warp.Type.PRIVATE)
                && Utils.normaliseString(warp.getCreatorName()).equals(owner)
                && filter.test(warp)
            ) {
                pool.add(new Candidate(key(warp), WarpLookup.address(warp, null), warp));
            }
        }
        return pool;
    }

    private static boolean isPrivateWarpOf(@Nullable UUID viewer, Warp warp) {
        return warp.isOfType(Warp.Type.PRIVATE) && warp.getCreatorId().equals(viewer);
    }

    private static String key(Warp warp) {
        return Utils.normaliseString(warp.getName()).trim();
    }

    /** The order that decides who stays when more match than fit: own warps, public ones, then shared; busiest first. */
    private static Comparator<Candidate> mostLikelyFirst(@Nullable UUID viewer) {
        return Comparator
            .comparingInt((Candidate c) -> isPrivateWarpOf(viewer, c.warp()) ? 0 : c.warp().isOfType(Warp.Type.PUBLIC) ? 1 : 2)
            .thenComparing((Candidate c) -> c.warp().getVisits(), Comparator.reverseOrder())
            .thenComparing(Candidate::text, String.CASE_INSENSITIVE_ORDER);
    }

    /** Candidates whose word at {@code wordIndex} passes {@code matches}, up to the cap. */
    private static List<Candidate> wordMatches(List<Candidate> candidates, int wordIndex, Predicate<String> matches) {
        return candidates.stream()
            .filter(candidate -> {
                String[] words = candidate.key().split(" ", wordIndex + 1);
                return wordIndex < words.length && matches.test(words[wordIndex]);
            })
            .limit(MAX_SUGGESTIONS)
            .toList();
    }

    private record Scored(Candidate candidate, double score) {}

    /** The few names closest to what was typed (lower is closer), for misspellings. */
    private static List<Candidate> closeSpellings(String search, List<Candidate> candidates) {
        return candidates.stream()
            .map(candidate -> new Scored(candidate, distance.apply(candidate.key(), search)))
            .filter(scored -> scored.score() <= DISTANCE_THRESHOLD)
            .sorted(Comparator.comparingDouble(Scored::score))
            .limit(FUZZY_SUGGESTIONS_LIMIT)
            .map(Scored::candidate)
            .toList();
    }
}
