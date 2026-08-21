package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpStoreTest {

    /** In-memory WarpFileIo tracking "written" paths, with injectable write/delete failure. */
    private static class FakeIo implements WarpFileIo {
        final Set<Path> files = new HashSet<>();
        boolean failWrite = false;
        boolean failDelete = false;

        public void write(Path path, Warp warp) throws IOException {
            if (failWrite) throw new IOException("injected write failure");
            files.add(path.normalize());
        }
        public boolean deleteIfExists(Path path) throws IOException {
            if (failDelete) throw new IOException("injected delete failure");
            return files.remove(path.normalize());
        }
        public boolean exists(Path path) {
            return files.contains(path.normalize());
        }
    }

    private static final UUID CREATOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir Path warpsDir;
    FakeIo io;
    WarpStore store;

    @BeforeEach
    void setup() {
        io = new FakeIo();
        store = new WarpStore(warpsDir, io);
    }

    private Warp warp(String name) {
        return new Warp(CREATOR, "Creator", name, "world",
            new SimpleLocation("world", 0, 64, 0, 0, 0), Warp.Type.PUBLIC, Instant.EPOCH);
    }

    private Path pathOf(Warp w) {
        return WarpPaths.resolveWarpPath(warpsDir, w).normalize();
    }

    // --- add ---------------------------------------------------------------

    @Test
    void addWritesTheFileThenRegistersTheWarp() throws Exception {
        Warp w = warp("Rivendell");
        store.add(w);
        assertSame(w, store.get("Rivendell"), "added warp is retrievable");
        assertTrue(io.exists(pathOf(w)), "file must be written");
    }

    @Test
    void addRejectsADuplicateName() throws Exception {
        store.add(warp("Rivendell"));
        assertThrows(WarpStore.WarpStoreException.class, () -> store.add(warp("rivendell")),
            "duplicate (normalised) name must be rejected");
    }

    @Test
    void addLeavesNoMapEntryWhenTheWriteFails() {
        io.failWrite = true;
        assertThrows(WarpStore.WarpStoreException.class, () -> store.add(warp("Rivendell")));
        assertFalse(store.exists("Rivendell"), "a failed write must not leave an in-memory warp (no orphan)");
    }

    // --- update: CORR-A ----------------------------------------------------

    @Test
    void updateOnWriteFailureLeavesTheOriginalWarpAndFileIntact() throws Exception {
        store.add(warp("Rivendell"));
        Warp before = store.get("Rivendell");
        Path beforePath = pathOf(before);

        io.failWrite = true;
        assertThrows(WarpStore.WarpStoreException.class,
            () -> store.update("Rivendell", w -> w.setWelcomeMessage("hi")));

        Warp after = store.get("Rivendell");
        assertNotNull(after, "warp must still exist after a failed update");
        assertEquals(1, store.size(), "no duplicate entry created");
        assertTrue(io.exists(beforePath), "original file must remain");
    }

    @Test
    void renameWritesTheNewFileAndDeletesTheOld() throws Exception {
        store.add(warp("Gondor"));
        Path oldPath = pathOf(warp("Gondor"));

        store.update("Gondor", w -> w.setName("Minas-Tirith"));

        assertFalse(store.exists("Gondor"), "old key gone");
        assertNotNull(store.get("Minas-Tirith"), "new key present");
        assertEquals(1, store.size(), "exactly one warp - no duplicate (the CORR-A rename bug)");
        assertFalse(io.exists(oldPath), "old file deleted");
        assertTrue(io.exists(pathOf(store.get("Minas-Tirith"))), "new file written");
    }

    // --- delete: CORR-B ----------------------------------------------------

    @Test
    void deleteRemovesTheFileThenTheMapEntry() throws Exception {
        Warp w = warp("Rivendell");
        store.add(w);
        store.delete(store.get("Rivendell"));
        assertFalse(store.exists("Rivendell"), "warp forgotten");
        assertFalse(io.exists(pathOf(w)), "file removed");
    }

    @Test
    void deleteKeepsTheWarpInMemoryWhenItsFileIsMissing() throws Exception {
        // The resurrection guard: if the file isn't where we compute it to be, do NOT drop the warp
        // from memory - otherwise a drifted/stale file reloads it on next boot (the _sic incident).
        store.add(warp("Rivendell"));
        io.files.clear(); // simulate the on-disk file having drifted away from its computed path

        assertThrows(WarpStore.WarpStoreException.class, () -> store.delete(store.get("Rivendell")));
        assertTrue(store.exists("Rivendell"), "warp must stay in memory when its file could not be deleted");
    }

    @Test
    void deleteKeepsTheWarpInMemoryWhenTheDeleteThrows() throws Exception {
        store.add(warp("Rivendell"));
        io.failDelete = true;
        assertThrows(WarpStore.WarpStoreException.class, () -> store.delete(store.get("Rivendell")));
        assertTrue(store.exists("Rivendell"), "a failed delete must not forget the warp");
    }
}
