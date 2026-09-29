package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpTest {

    private static final UUID CREATOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID NEW_MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void copyConstructorIsolatesTheMembersSet() {
        // WarpStore.update relies on `new Warp(current)` being an isolated copy so a failed write
        // never touches the live warp. If the copy aliases the original's members set, that
        // isolation (and CORR-A crash-safety for members) is defeated.
        Warp original = new Warp(CREATOR, "Creator", "base", "world",
            new SimpleLocation("world", 0, 64, 0, 0, 0), Warp.Type.PRIVATE, Instant.EPOCH);
        original.addMember(MEMBER);

        Warp copy = new Warp(original);
        copy.addMember(NEW_MEMBER);

        assertTrue(original.getMembers().contains(MEMBER), "original keeps its own member");
        assertFalse(original.getMembers().contains(NEW_MEMBER),
            "mutating the copy's members must not leak into the original - the copy must be isolated");
    }
}
