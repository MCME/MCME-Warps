package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpStoreTest {

    /** In-memory WarpFileIo tracking "written" paths, with injectable write/delete failure. */
    private static class FakeIo implements WarpFileIo {
        final Set<Path> files = new HashSet<>();
        final List<Path> writeLog = new ArrayList<>();          // every write() call, in order
        final Map<Path, Integer> writtenVisits = new HashMap<>(); // visits count as written
        boolean failWrite = false;
        boolean failDelete = false;

        public void write(Path path, Warp warp) throws IOException {
            if (failWrite) throw new IOException("injected write failure");
            files.add(path.normalize());
            writeLog.add(path.normalize());
            writtenVisits.put(path.normalize(), warp.getVisits());
        }
        public boolean deleteIfExists(Path path) throws IOException {
            if (failDelete) throw new IOException("injected delete failure");
            return files.remove(path.normalize());
        }
        public boolean exists(Path path) {
            return files.contains(path.normalize());
        }
    }

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b0");

    @TempDir Path warpsDir;
    FakeIo io;
    WarpStore store;

    @BeforeEach
    void setup() {
        io = new FakeIo();
        store = new WarpStore(warpsDir, io);
    }

    private Warp pub(String name) {
        return new Warp(ALICE, "Alice", name, "world",
            new SimpleLocation("world", 0, 64, 0, 0, 0), Warp.Type.PUBLIC, Instant.EPOCH);
    }

    private Warp priv(UUID creator, String name) {
        return new Warp(creator, "someone", name, "hub",
            new SimpleLocation("hub", 0, 64, 0, 0, 0), Warp.Type.PRIVATE, Instant.EPOCH);
    }

    private Path pathOf(Warp w) {
        return WarpPaths.resolveWarpPath(warpsDir, w).normalize();
    }

    // --- add / public ------------------------------------------------------

    @Test
    void addWritesTheFileThenRegistersAPublicWarp() throws Exception {
        Warp w = pub("Rivendell");
        store.add(w);
        assertSame(w, store.getPublic("Rivendell"), "added public warp is retrievable");
        assertTrue(io.exists(pathOf(w)), "file must be written");
    }

    @Test
    void addRejectsADuplicatePublicName() throws Exception {
        store.add(pub("Rivendell"));
        assertThrows(WarpStore.WarpStoreException.class, () -> store.add(pub("rivendell")));
    }

    @Test
    void addLeavesNoMapEntryWhenTheWriteFails() {
        io.failWrite = true;
        assertThrows(WarpStore.WarpStoreException.class, () -> store.add(pub("Rivendell")));
        assertFalse(store.publicExists("Rivendell"), "a failed write must not leave an in-memory warp");
    }

    // --- namespacing (root-fix #1) -----------------------------------------

    @Test
    void twoPlayersCanEachOwnAPrivateWarpOfTheSameName() throws Exception {
        store.add(priv(ALICE, "home"));
        store.add(priv(BOB, "home"));
        assertNotNull(store.getPrivate(ALICE, "home"), "Alice keeps her home");
        assertNotNull(store.getPrivate(BOB, "home"), "Bob keeps his home");
        assertEquals(2, store.size(), "both survive - private names are per-creator");
    }

    @Test
    void resolvePrefersTheSendersPrivateWarpOverAPublicOne() throws Exception {
        store.add(pub("home"));
        store.add(priv(ALICE, "home"));
        assertSame(store.getPrivate(ALICE, "home"), store.resolve("home", ALICE),
            "your own private warp shadows a public one of the same name");
    }

    @Test
    void resolveFallsThroughToPublicWhenTheSenderHasNoPrivateWarp() throws Exception {
        store.add(pub("home"));
        assertSame(store.getPublic("home"), store.resolve("home", BOB),
            "a player without a private 'home' gets the public one");
        assertSame(store.getPublic("home"), store.resolve("home", null),
            "a null sender (console) resolves to public");
    }

    @Test
    void aPrivateWarpAndAPublicWarpOfTheSameNameCoexist() throws Exception {
        store.add(pub("home"));
        store.add(priv(ALICE, "home"));           // must NOT collide - different namespaces
        assertEquals(2, store.size());
    }

    // --- update: rename, move, type change ---------------------------------

    @Test
    void updateOnWriteFailureLeavesTheOriginalWarpAndFileIntact() throws Exception {
        store.add(pub("Rivendell"));
        Warp before = store.getPublic("Rivendell");
        io.failWrite = true;
        assertThrows(WarpStore.WarpStoreException.class,
            () -> store.update(before, w -> w.setWelcomeMessage("hi")));
        assertNotNull(store.getPublic("Rivendell"), "warp still exists after a failed update");
        assertEquals(1, store.size());
        assertTrue(io.exists(pathOf(before)), "original file remains");
    }

    @Test
    void renameWritesTheNewFileAndDeletesTheOld() throws Exception {
        store.add(pub("Gondor"));
        Path oldPath = pathOf(pub("Gondor"));
        store.update(store.getPublic("Gondor"), w -> w.setName("Minas-Tirith"));
        assertNull(store.getPublic("Gondor"), "old key gone");
        assertNotNull(store.getPublic("Minas-Tirith"), "new key present");
        assertEquals(1, store.size());
        assertFalse(io.exists(oldPath), "old file deleted");
    }

    @Test
    void setPublicMovesAPrivateWarpIntoThePublicNamespace() throws Exception {
        store.add(priv(ALICE, "tower"));
        Path oldPath = pathOf(priv(ALICE, "tower"));
        store.update(store.getPrivate(ALICE, "tower"), w -> w.setType(Warp.Type.PUBLIC));
        assertNull(store.getPrivate(ALICE, "tower"), "gone from Alice's private map");
        assertNotNull(store.getPublic("tower"), "now a public warp");
        assertFalse(io.exists(oldPath), "old private file deleted");
    }

    @Test
    void setPublicIsRejectedWhenThePublicNameIsTaken() throws Exception {
        store.add(pub("tower"));
        store.add(priv(ALICE, "tower"));
        assertThrows(WarpStore.WarpStoreException.class,
            () -> store.update(store.getPrivate(ALICE, "tower"), w -> w.setType(Warp.Type.PUBLIC)),
            "can't make a private warp public when a public warp of that name exists");
        assertNotNull(store.getPrivate(ALICE, "tower"), "the private warp is untouched");
    }

    // --- delete: CORR-B ----------------------------------------------------

    @Test
    void deleteRemovesTheFileThenTheMapEntry() throws Exception {
        store.add(priv(ALICE, "secret"));
        store.delete(store.getPrivate(ALICE, "secret"));
        assertNull(store.getPrivate(ALICE, "secret"), "warp forgotten");
    }

    @Test
    void deleteKeepsTheWarpInMemoryWhenItsFileIsMissing() throws Exception {
        store.add(pub("Rivendell"));
        io.files.clear();
        assertThrows(WarpStore.WarpStoreException.class, () -> store.delete(store.getPublic("Rivendell")));
        assertTrue(store.publicExists("Rivendell"), "warp stays in memory when its file could not be deleted");
    }

    @Test
    void deleteKeepsTheWarpInMemoryWhenTheDeleteThrows() throws Exception {
        store.add(pub("Rivendell"));
        io.failDelete = true;
        assertThrows(WarpStore.WarpStoreException.class, () -> store.delete(store.getPublic("Rivendell")));
        assertTrue(store.publicExists("Rivendell"), "a failed delete must not forget the warp");
    }

    // --- visit flush through the seam (root cause #3a) ----------------------

    /** Collects (warp, error) pairs a flush reports, so tests can assert on failures. */
    private static final class Errors {
        final List<String> messages = new ArrayList<>();
        void record(Warp w, Exception e) { messages.add(w.getName() + ": " + e.getMessage()); }
    }

    @Test
    void recordVisitIncrementsTheInMemoryVisitCount() throws Exception {
        Warp w = pub("Rivendell");
        store.add(w);
        store.recordVisit(w);
        store.recordVisit(w);
        assertEquals(2, w.getVisits(), "each visit bumps the counter");
    }

    @Test
    void flushVisitsWritesOnlyWarpsThatWereVisited() throws Exception {
        store.add(pub("Visited"));
        store.add(pub("Untouched"));
        Path visited = pathOf(pub("Visited"));
        Path untouched = pathOf(pub("Untouched"));
        io.writeLog.clear(); // ignore the writes from add()

        store.recordVisit(store.getPublic("Visited"));
        store.flushVisits(new Errors()::record);

        assertTrue(io.writeLog.contains(visited), "the visited warp is flushed");
        assertFalse(io.writeLog.contains(untouched), "an unvisited warp is not rewritten (no 14k-file flush)");
    }

    @Test
    void flushVisitsPersistsTheCurrentVisitCount() throws Exception {
        Warp w = pub("Rivendell");
        store.add(w);
        store.recordVisit(w);
        store.recordVisit(w);
        store.flushVisits(new Errors()::record);
        assertEquals(2, io.writtenVisits.get(pathOf(w)), "the flushed file carries the incremented count");
    }

    @Test
    void flushVisitsClearsDirtyStateSoASecondFlushWritesNothing() throws Exception {
        Warp w = pub("Rivendell");
        store.add(w);
        store.recordVisit(w);
        store.flushVisits(new Errors()::record);
        io.writeLog.clear();

        store.flushVisits(new Errors()::record);
        assertTrue(io.writeLog.isEmpty(), "nothing left dirty after the first flush");
    }

    @Test
    void flushVisitsSkipsAWarpDeletedAfterBeingVisited() throws Exception {
        Warp w = pub("Doomed");
        store.add(w);
        store.recordVisit(w);
        store.delete(w);
        io.writeLog.clear();

        Errors errors = new Errors();
        store.flushVisits(errors::record);

        assertTrue(io.writeLog.isEmpty(), "a deleted warp is not resurrected by the visit flush");
        assertTrue(errors.messages.isEmpty(), "and it is not reported as an error");
    }

    @Test
    void flushVisitsWritesTheCurrentObjectAfterAnUpdateNotAStaleReference() throws Exception {
        Warp original = pub("Rivendell");
        store.add(original);
        store.recordVisit(original);                 // dirty ref = the original object, visits = 1
        store.update(original, w -> w.setWelcomeMessage("hi")); // swaps a copy into the map
        io.writeLog.clear();

        store.flushVisits(new Errors()::record);

        Warp current = store.getPublic("Rivendell");
        assertEquals("hi", current.getWelcomeMessage(), "the update stands");
        assertEquals(1, current.getVisits(), "the visit rode onto the updated copy");
        assertEquals(1, io.writtenVisits.get(pathOf(current)), "flush persisted the live object, not the stale one");
    }

    @Test
    void flushVisitsReportsWriteFailuresWithoutThrowing() throws Exception {
        Warp w = pub("Rivendell");
        store.add(w);
        store.recordVisit(w);
        io.failWrite = true;

        Errors errors = new Errors();
        store.flushVisits(errors::record); // must not throw

        assertEquals(1, errors.messages.size(), "the failed flush is reported via the callback");
    }

    // --- atomic load-into-fresh-map-then-swap (root cause #3b) --------------

    @Test
    void replaceAllInstallsTheNewSetAndDropsTheOld() {
        store.replaceAll(List.of(pub("Old")), (w, why) -> {});
        store.replaceAll(List.of(pub("New1"), pub("New2")), (w, why) -> {});

        assertNull(store.getPublic("Old"), "warps from the previous load are gone");
        assertNotNull(store.getPublic("New1"));
        assertNotNull(store.getPublic("New2"));
        assertEquals(2, store.size());
    }

    @Test
    void replaceAllRoutesPublicAndPrivateWarpsIntoTheirNamespaces() {
        store.replaceAll(List.of(pub("spawn"), priv(ALICE, "home")), (w, why) -> {});

        assertNotNull(store.getPublic("spawn"));
        assertNotNull(store.getPrivate(ALICE, "home"));
        assertEquals(2, store.size());
    }

    @Test
    void replaceAllSkipsADuplicateKeyAndReportsIt() {
        List<Warp> skipped = new ArrayList<>();
        store.replaceAll(List.of(pub("dup"), pub("DUP")), (w, why) -> skipped.add(w));

        assertEquals(1, store.size(), "a same-key duplicate is not installed twice");
        assertEquals(1, skipped.size(), "the collision is reported to the caller (which logs the file)");
    }

    @Test
    void replaceAllClearsVisitDirtyStateSoAStaleVisitIsNotFlushedOntoTheReloadedWarp() throws Exception {
        Warp oldSpawn = pub("spawn");
        store.add(oldSpawn);
        store.recordVisit(oldSpawn);                       // dirty, referencing the pre-reload object
        store.replaceAll(List.of(pub("spawn")), (w, why) -> {}); // a reload brings a fresh "spawn"
        io.writeLog.clear();

        store.flushVisits(new Errors()::record);
        assertTrue(io.writeLog.isEmpty(),
            "a reload discards pending visits; the stale count is not written onto the freshly loaded warp");
    }

    @Test
    void addAfterReplaceAllTargetsTheNewSnapshot() throws Exception {
        store.replaceAll(List.of(pub("A")), (w, why) -> {});
        store.add(pub("B"));

        assertNotNull(store.getPublic("A"));
        assertNotNull(store.getPublic("B"), "a live write lands in the swapped-in snapshot");
        assertEquals(2, store.size());
    }
}
