package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpManagerPathTest {

    private static final UUID CREATOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static Warp publicWarp(String name, String server, String world) {
        return new Warp(CREATOR, "Creator", name, server,
            new SimpleLocation(world, 0, 64, 0, 0, 0), Warp.Type.PUBLIC, Instant.EPOCH);
    }

    private static Warp privateWarp(String name) {
        return new Warp(CREATOR, "Creator", name, "hub",
            new SimpleLocation("hub", 0, 64, 0, 0, 0), Warp.Type.PRIVATE, Instant.EPOCH);
    }

    @Test
    void legitPublicWarpResolvesUnderTheWarpsDir(@TempDir Path warps) {
        Path p = WarpPaths.resolveWarpPath(warps, publicWarp("Rivendell", "world", "world"));
        assertTrue(p.startsWith(warps.normalize()), "legit warp must resolve inside the warps dir");
        assertEquals("rivendell.yml", p.getFileName().toString(), "name is normalised (lowercased) + .yml");
    }

    @Test
    void legitPrivateWarpResolvesUnderPrivateWarps(@TempDir Path warps) {
        Path p = WarpPaths.resolveWarpPath(warps, privateWarp("zzz-creator-base"));
        assertTrue(p.startsWith(warps.resolve("private-warps").resolve(CREATOR.toString())),
            "private warps live under private-warps/<creator-uuid>/");
    }

    @Test
    void warpNameEscapingViaDotDotIsRejected(@TempDir Path warps) {
        // The SEC-1 vector: a forged plugin message supplies a traversal name.
        Warp evil = publicWarp("../../../../../../tmp/pwn", "world", "world");
        assertThrows(IllegalArgumentException.class,
            () -> WarpPaths.resolveWarpPath(warps, evil),
            "a warp name that escapes the warps dir must be refused");
    }

    @Test
    void worldSegmentEscapingIsRejected(@TempDir Path warps) {
        // world arrives from the backend and is also a path segment.
        Warp evil = publicWarp("safe", "world", "../../../../etc");
        assertThrows(IllegalArgumentException.class,
            () -> WarpPaths.resolveWarpPath(warps, evil),
            "a world name that escapes the warps dir must be refused");
    }
}
