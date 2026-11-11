package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Shared fixtures for the warp tests: three players, an in-memory store and warp factories. */
public final class TestWarps {

    public static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    public static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-0000000000c5");

    private TestWarps() {}

    /** A {@link WarpFileIo} that only remembers which paths were written. */
    public static final class InMemoryIo implements WarpFileIo {
        public final Set<Path> files = new HashSet<>();

        public void write(Path path, Warp warp) { files.add(path.normalize()); }
        public boolean deleteIfExists(Path path) { return files.remove(path.normalize()); }
        public boolean exists(Path path) { return files.contains(path.normalize()); }
    }

    public static WarpStore store(Path warpsDir) {
        return new WarpStore(warpsDir, new InMemoryIo());
    }

    public static Warp publicWarp(String name) {
        return new Warp(CAROL, "Carol", name, "world",
            new SimpleLocation("world", 0, 64, 0, 0, 0), Warp.Type.PUBLIC, Instant.EPOCH);
    }

    public static Warp privateWarp(UUID creator, String creatorName, String name) {
        return new Warp(creator, creatorName, name, "hub",
            new SimpleLocation("hub", 0, 64, 0, 0, 0), Warp.Type.PRIVATE, Instant.EPOCH);
    }
}
