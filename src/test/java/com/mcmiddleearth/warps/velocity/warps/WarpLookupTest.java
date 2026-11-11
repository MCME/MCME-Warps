package com.mcmiddleearth.warps.velocity.warps;

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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What a warp name typed by a player reaches. Written for forum report 7913: a private warp of
 * somebody else was offered in tab-completion but could never be looked up - by staff or by members.
 */
class WarpLookupTest {

    private static final Predicate<Warp> ANY = w -> true;
    private static final Predicate<Warp> NONE = w -> false;

    @TempDir Path warpsDir;
    WarpStore store;

    @BeforeEach
    void setup() {
        store = TestWarps.store(warpsDir);
    }

    /** What a regular player may see of other people's private warps: only those they are a member of. */
    private static Predicate<Warp> memberOnly(UUID player) {
        return w -> w.getMembers().contains(player);
    }

    private Warp found(WarpLookup.Result result) {
        return assertInstanceOf(WarpLookup.Found.class, result).warp();
    }

    // --- the order: your own private warp, then a public warp, then somebody else's ------------

    @Test
    void yourOwnPrivateWarpComesBeforeAPublicOneOfTheSameName() throws Exception {
        store.add(publicWarp("home"));
        store.add(privateWarp(ALICE, "Alice", "home"));

        assertSame(store.getPrivate(ALICE, "home"), found(WarpLookup.find(store, "home", ALICE, ANY, ANY)));
    }

    @Test
    void aNameYouDoNotOwnReachesThePublicWarp() throws Exception {
        store.add(publicWarp("home"));
        store.add(privateWarp(ALICE, "Alice", "home"));

        assertSame(store.getPublic("home"), found(WarpLookup.find(store, "home", BOB, NONE, ANY)));
        assertSame(store.getPublic("home"), found(WarpLookup.find(store, "home", null, NONE, ANY)),
            "the console has no private warps of its own");
    }

    @Test
    void aMemberReachesTheOwnersPrivateWarpByItsBareName() throws Exception {
        Warp hideout = privateWarp(ALICE, "Alice", "hideout");
        hideout.addMember(BOB);
        store.add(hideout);

        assertSame(hideout, found(WarpLookup.find(store, "hideout", BOB, memberOnly(BOB), ANY)));
    }

    @Test
    void staffReachAPrivateWarpTheyAreAllowedToSee() throws Exception {
        Warp hideout = privateWarp(ALICE, "Alice", "hideout");
        store.add(hideout);

        // mcmewarps.override.use: every warp is visible
        assertSame(hideout, found(WarpLookup.find(store, "hideout", CAROL, ANY, ANY)));
    }

    @Test
    void aPrivateWarpYouMayNotSeeLooksLikeItDoesNotExist() throws Exception {
        store.add(privateWarp(ALICE, "Alice", "hideout"));

        assertInstanceOf(WarpLookup.NotFound.class, WarpLookup.find(store, "hideout", BOB, memberOnly(BOB), ANY));
        assertInstanceOf(WarpLookup.NotFound.class, WarpLookup.find(store, "Alice:hideout", BOB, memberOnly(BOB), ANY));
    }

    @Test
    void somebodyElsesPrivateWarpNeverTakesTheNameOfAPublicWarp() throws Exception {
        // A creator adds members without asking them, so a private "Minas Tirith" with Bob as a member
        // must not capture Bob's "/warp Minas Tirith".
        Warp trap = privateWarp(ALICE, "Alice", "Minas Tirith");
        trap.addMember(BOB);
        store.add(trap);
        store.add(publicWarp("Minas Tirith"));

        assertSame(store.getPublic("Minas Tirith"),
            found(WarpLookup.find(store, "Minas Tirith", BOB, memberOnly(BOB), ANY)));
    }

    @Test
    void somebodyElsesPrivateWarpNeverTakesTheNameOfYourOwn() throws Exception {
        Warp theirs = privateWarp(ALICE, "Alice", "home");
        theirs.addMember(BOB);
        store.add(theirs);
        store.add(privateWarp(BOB, "Bob", "home"));

        assertSame(store.getPrivate(BOB, "home"), found(WarpLookup.find(store, "home", BOB, memberOnly(BOB), ANY)));
    }

    // --- owner:name ------------------------------------------------------------------------------

    @Test
    void ownerColonNameReachesThatPlayersPrivateWarp() throws Exception {
        store.add(privateWarp(ALICE, "Alice", "home"));
        store.add(privateWarp(BOB, "Bob", "home"));

        assertSame(store.getPrivate(ALICE, "home"), found(WarpLookup.find(store, "Alice:home", CAROL, ANY, ANY)));
        assertSame(store.getPrivate(BOB, "home"), found(WarpLookup.find(store, "Bob:home", CAROL, ANY, ANY)));
    }

    @Test
    void ownerColonNameIgnoresCaseAccentsAndSpacesAroundTheColon() throws Exception {
        Warp warp = privateWarp(ALICE, "Alice", "Amon Dîn camp");
        store.add(warp);

        assertSame(warp, found(WarpLookup.find(store, "alice:amon din camp", CAROL, ANY, ANY)));
        assertSame(warp, found(WarpLookup.find(store, "ALICE : Amon Dîn camp", CAROL, ANY, ANY)));
    }

    @Test
    void ownerColonNameAlsoWorksForYourOwnWarpAndFromTheConsole() throws Exception {
        Warp warp = privateWarp(ALICE, "Alice", "home");
        store.add(warp);

        assertSame(warp, found(WarpLookup.find(store, "Alice:home", ALICE, ANY, ANY)));
        assertSame(warp, found(WarpLookup.find(store, "Alice:home", null, ANY, ANY)));
    }

    @Test
    void aBareNameSharedByTwoPlayersAsksWhichOneYouMean() throws Exception {
        store.add(privateWarp(BOB, "Bob", "home"));
        store.add(privateWarp(ALICE, "Alice", "home"));

        WarpLookup.Ambiguous ambiguous =
            assertInstanceOf(WarpLookup.Ambiguous.class, WarpLookup.find(store, "home", CAROL, ANY, ANY));
        assertEquals(List.of("Alice", "Bob"), ambiguous.candidates().stream().map(Warp::getCreatorName).toList(),
            "both owners, in alphabetical order, so the message can name them");
    }

    @Test
    void anUnknownOwnerOrAHalfTypedAddressIsNotFound() throws Exception {
        store.add(privateWarp(ALICE, "Alice", "home"));

        for (String input : List.of("Nobody:home", "Alice:", ":home", ":", "Alice:nothing", "nothing")) {
            assertInstanceOf(WarpLookup.NotFound.class, WarpLookup.find(store, input, CAROL, ANY, ANY), input);
        }
    }

    // --- allowed: what the command needs (use it, or modify it) -----------------------------------

    @Test
    void aWarpYouMaySeeButNotActOnIsRefusedNotHidden() throws Exception {
        Warp hideout = privateWarp(ALICE, "Alice", "hideout");
        hideout.addMember(BOB);
        store.add(hideout);
        Predicate<Warp> ownedByBob = w -> w.getCreatorId().equals(BOB); // e.g. "/warpmanager delete"

        WarpLookup.NotAllowed refused = assertInstanceOf(WarpLookup.NotAllowed.class,
            WarpLookup.find(store, "hideout", BOB, memberOnly(BOB), ownedByBob));
        assertSame(hideout, refused.warp());
    }

    @Test
    void aPublicWarpYouMayNotUseIsRefused() throws Exception {
        store.add(publicWarp("Moria")); // e.g. no mcmewarps.world-access for its world

        assertInstanceOf(WarpLookup.NotAllowed.class, WarpLookup.find(store, "Moria", BOB, NONE, NONE));
    }

    @Test
    void whenOnlyOneOfTheSameNamedWarpsIsAllowedItIsTheOneYouGet() throws Exception {
        Warp alices = privateWarp(ALICE, "Alice", "base");
        Warp bobs = privateWarp(BOB, "Bob", "base");
        store.add(alices);
        store.add(bobs);
        Predicate<Warp> onlyAlices = w -> w.getCreatorId().equals(ALICE);

        assertSame(alices, found(WarpLookup.find(store, "base", CAROL, ANY, onlyAlices)));
    }

    // --- the address a player types to reach a warp ---------------------------------------------

    @Test
    void theAddressOfAWarpIsItsNameUnlessItIsSomebodyElsesPrivateWarp() {
        assertEquals("Rivendell", WarpLookup.address(publicWarp("Rivendell"), ALICE));
        assertEquals("home", WarpLookup.address(privateWarp(ALICE, "Alice", "home"), ALICE));
        assertEquals("Alice:home", WarpLookup.address(privateWarp(ALICE, "Alice", "home"), BOB));
        assertEquals("Alice:home", WarpLookup.address(privateWarp(ALICE, "Alice", "home"), null));
    }

    @Test
    void everyAddressLeadsBackToItsWarp() throws Exception {
        Warp shared = privateWarp(ALICE, "Alice", "home");
        shared.addMember(BOB);
        store.add(shared);
        store.add(privateWarp(BOB, "Bob", "home"));
        store.add(publicWarp("home"));
        store.add(publicWarp("Rivendell"));
        store.add(privateWarp(CAROL, "Carol", "tower"));

        for (Warp warp : store.all()) {
            // Bob's own "home" is what the bare name reaches for him, so the public "home" is out of his reach
            if (warp == store.getPublic("home")) continue;
            String address = WarpLookup.address(warp, BOB);
            assertSame(warp, found(WarpLookup.find(store, address, BOB, ANY, ANY)), address);
        }
    }
}
