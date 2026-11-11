package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.velocity.warps.TestWarps;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpLookup;
import com.mcmiddleearth.warps.velocity.warps.WarpStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static com.mcmiddleearth.warps.velocity.warps.TestWarps.ALICE;
import static com.mcmiddleearth.warps.velocity.warps.TestWarps.BOB;
import static com.mcmiddleearth.warps.velocity.warps.TestWarps.CAROL;
import static com.mcmiddleearth.warps.velocity.warps.TestWarps.privateWarp;
import static com.mcmiddleearth.warps.velocity.warps.TestWarps.publicWarp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which warps are offered while a player types a name. Written for the staff report that other
 * players' private warps crowd the list, push public warps out of it, and cannot be used anyway.
 */
class WarpSuggestionsTest {

    /** A staff member with mcmewarps.override.use: the command lets them use every warp. */
    private static final Predicate<Warp> EVERY_WARP = w -> true;

    @TempDir Path warpsDir;
    WarpStore store;

    @BeforeEach
    void setup() {
        store = TestWarps.store(warpsDir);
    }

    private List<String> offered(UUID viewer, Predicate<Warp> filter, String typed) {
        return WarpSuggestions.select(store.all(), viewer, filter, typed).stream()
            .map(WarpSuggestions.Entry::text)
            .sorted()
            .toList();
    }

    // --- other players' private warps stay out of the way until asked for ---------------------

    @Test
    void staffAreNotOfferedOtherPlayersPrivateWarpsWhileTypingAPlainName() throws Exception {
        store.add(publicWarp("Minas Tirith"));
        store.add(privateWarp(ALICE, "Alice", "mine"));
        store.add(privateWarp(BOB, "Bob", "minecart"));

        assertEquals(List.of("Minas Tirith"), offered(CAROL, EVERY_WARP, "min"));
    }

    @Test
    void typingOwnerColonOffersThatPlayersPrivateWarps() throws Exception {
        store.add(publicWarp("Minas Tirith"));
        store.add(privateWarp(ALICE, "Alice", "mine"));
        store.add(privateWarp(ALICE, "Alice", "castle"));
        store.add(privateWarp(BOB, "Bob", "minecart"));

        assertEquals(List.of("Alice:castle", "Alice:mine"), offered(CAROL, EVERY_WARP, "alice:"));
        assertEquals(List.of("Alice:mine"), offered(CAROL, EVERY_WARP, "alice:mi"));
        assertEquals(List.of("Alice:mine"), offered(CAROL, EVERY_WARP, "\"Alice:mi"),
            "commands that need quotes around a name start the input with a quote");
    }

    @Test
    void ownerColonOnlyOffersWhatTheCommandAllows() throws Exception {
        Warp shared = privateWarp(ALICE, "Alice", "hideout");
        shared.addMember(BOB);
        store.add(shared);
        store.add(privateWarp(ALICE, "Alice", "secret"));
        Predicate<Warp> usableByBob = w -> w.getMembers().contains(BOB) || w.getCreatorId().equals(BOB);

        assertEquals(List.of("Alice:hideout"), offered(BOB, usableByBob, "alice:"));
    }

    @Test
    void aWarpSharedWithYouIsOfferedUnderItsOwnersName() throws Exception {
        Warp shared = privateWarp(ALICE, "Alice", "hideout");
        shared.addMember(BOB);
        store.add(shared);
        store.add(privateWarp(BOB, "Bob", "hill"));
        store.add(publicWarp("Hithlum"));

        assertEquals(List.of("Alice:hideout", "Hithlum", "hill"), offered(BOB, EVERY_WARP, "hi"));
    }

    @Test
    void yourOwnPrivateWarpReplacesThePublicWarpOfTheSameName() throws Exception {
        store.add(publicWarp("home"));
        Warp mine = privateWarp(BOB, "Bob", "home");
        store.add(mine);

        List<WarpSuggestions.Entry> entries = WarpSuggestions.select(store.all(), BOB, EVERY_WARP, "ho");
        assertEquals(1, entries.size(), "one 'home', not two that look the same");
        assertSame(mine, entries.get(0).warp(), "the name reaches Bob's own warp, so that is the one shown");
    }

    // --- the cap of 50 keeps the warps a player most likely wants --------------------------------

    @Test
    void theCapKeepsYourOwnWarpsThenTheMostVisitedPublicOnes() throws Exception {
        for (int i = 0; i < 70; i++) {
            Warp crowd = publicWarp("mine " + i);
            crowd.setVisits(i);
            store.add(crowd);
        }
        store.add(privateWarp(BOB, "Bob", "minecart"));

        List<String> offered = offered(BOB, EVERY_WARP, "min");
        assertEquals(WarpSuggestions.MAX_SUGGESTIONS, offered.size());
        assertTrue(offered.contains("minecart"), "Bob's own warp is never pushed out");
        assertTrue(offered.contains("mine 69"), "the most visited public warp stays");
        assertFalse(offered.contains("mine 0"), "the least visited one is what gives way");
    }

    @Test
    void anEmptyInputOffersUpToTheCap() throws Exception {
        for (int i = 0; i < 60; i++) store.add(publicWarp("warp " + i));

        assertEquals(WarpSuggestions.MAX_SUGGESTIONS, offered(BOB, EVERY_WARP, "").size());
    }

    // --- the matching itself is unchanged ------------------------------------------------------------

    @Test
    void matchesTheStartOfAWordThenAnywhereInAWordThenCloseSpellings() throws Exception {
        store.add(publicWarp("Minas Tirith"));
        store.add(publicWarp("Dol Amroth"));
        store.add(publicWarp("Amon Dîn"));

        assertEquals(List.of("Minas Tirith"), offered(BOB, EVERY_WARP, "mi"), "start of the first word");
        assertEquals(List.of("Minas Tirith"), offered(BOB, EVERY_WARP, "minas ti"), "start of the second word");
        assertEquals(List.of("Dol Amroth"), offered(BOB, EVERY_WARP, "ol"), "inside a word, when nothing starts with it");
        assertEquals(List.of("Amon Dîn"), offered(BOB, EVERY_WARP, "amon din"), "accents do not matter");
        assertEquals(List.of("Minas Tirith"), offered(BOB, EVERY_WARP, "minas tirth"), "a close misspelling");
    }

    // --- nothing is offered that cannot be used ---------------------------------------------------

    @Test
    void everythingOfferedCanBeLookedUpAgain() throws Exception {
        Warp shared = privateWarp(ALICE, "Alice", "home");
        shared.addMember(BOB);
        store.add(shared);
        store.add(privateWarp(ALICE, "Alice", "castle"));
        store.add(privateWarp(BOB, "Bob", "home"));
        store.add(privateWarp(CAROL, "Carol", "home"));
        store.add(publicWarp("home"));
        store.add(publicWarp("Hobbiton"));

        for (UUID viewer : List.of(ALICE, BOB, CAROL)) {
            for (String typed : List.of("", "h", "ho", "alice:", "alice:h", "bob:", "carol:ho")) {
                for (WarpSuggestions.Entry entry : WarpSuggestions.select(store.all(), viewer, EVERY_WARP, typed)) {
                    WarpLookup.Result result = WarpLookup.find(store, entry.text(), viewer, EVERY_WARP, EVERY_WARP);
                    assertSame(entry.warp(), assertInstanceOf(WarpLookup.Found.class, result).warp(),
                        "'%s' was offered for '%s' but does not lead to that warp".formatted(entry.text(), typed));
                }
            }
        }
    }
}
