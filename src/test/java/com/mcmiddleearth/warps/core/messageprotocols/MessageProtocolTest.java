package com.mcmiddleearth.warps.core.messageprotocols;

import com.mcmiddleearth.warps.core.SimpleLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wire-protocol contract (root cause #2): every message carries a [version][opcode] header,
 * opcodes are stable numbers (not enum names), and any version mismatch / unknown opcode /
 * truncation / trailing junk is rejected as a {@link MalformedMessageException} rather than
 * silently mis-decoded.
 */
class MessageProtocolTest {

    private static final SimpleLocation LOC = new SimpleLocation("world", 1.5, 64.0, -2.5, 90.0f, -12.0f);

    // --- stable opcodes: the wire numbers must never drift, or two jars stop understanding each other.

    @Test
    void locationActionSubchannelOpcodesAreStable() {
        assertEquals(1, LocationActionSubchannel.CREATE_PRIVATE.code());
        assertEquals(2, LocationActionSubchannel.CREATE_PUBLIC.code());
        assertEquals(3, LocationActionSubchannel.MOVE.code());
    }

    @Test
    void teleportSubchannelOpcodesAreStable() {
        assertEquals(1, TeleportMessage.Subchannel.SAME_SERVER.code());
        assertEquals(2, TeleportMessage.Subchannel.DIFF_SERVER.code());
        assertEquals(3, TeleportMessage.Subchannel.LOCAL_WARP.code());
    }

    @Test
    void teleportResultOpcodesAreStable() {
        assertEquals(1, TeleportResult.Subchannel.TELEPORT_RESULT.code());
        assertEquals(1, TeleportResult.ResultType.SUCCESS.code());
    }

    @Test
    void miscOpcodesAreStable() {
        assertEquals(1, MiscMessage.Subchannel.UPDATE_COMMANDS.code());
    }

    // --- round trips: serialise then read returns an equal value for every message type.

    @Test
    void roundTripsARequestLocationMessage() throws MalformedMessageException {
        byte[] bytes = RequestLocationMessage.serialise(LocationActionSubchannel.CREATE_PRIVATE, "home");
        RequestLocationMessage.Result result = RequestLocationMessage.read(bytes);

        assertEquals(LocationActionSubchannel.CREATE_PRIVATE, result.subchannel());
        assertEquals("home", result.warpName());
    }

    @Test
    void roundTripsAPlayerLocationMessage() throws MalformedMessageException {
        byte[] bytes = PlayerLocationMessage.serialise(LocationActionSubchannel.MOVE, LOC, "spawn");
        PlayerLocationMessage.Result result = PlayerLocationMessage.read(bytes);

        assertEquals(LocationActionSubchannel.MOVE, result.subchannel());
        assertEquals(LOC, result.warpLocation());
        assertEquals("spawn", result.warpName());
    }

    @Test
    void roundTripsATeleportMessage() throws MalformedMessageException {
        byte[] bytes = TeleportMessage.serialise(TeleportMessage.Subchannel.DIFF_SERVER, "spawn", LOC);
        TeleportMessage.Result result = TeleportMessage.read(bytes);

        assertEquals(TeleportMessage.Subchannel.DIFF_SERVER, result.subchannel());
        assertEquals("spawn", result.warpName());
        assertEquals(LOC, result.data());
    }

    @Test
    void roundTripsATeleportResult() throws MalformedMessageException {
        byte[] bytes = TeleportResult.serialise("spawn", TeleportResult.ResultType.SUCCESS);
        TeleportResult.Response response = TeleportResult.read(bytes);

        assertEquals(TeleportResult.Subchannel.TELEPORT_RESULT, response.subchannel());
        assertEquals("spawn", response.warpName());
        assertEquals(TeleportResult.ResultType.SUCCESS, response.resultType());
    }

    @Test
    void roundTripsAMiscMessage() throws MalformedMessageException {
        byte[] bytes = MiscMessage.serialise(MiscMessage.Subchannel.UPDATE_COMMANDS);
        MiscMessage.Result result = MiscMessage.read(bytes);

        assertEquals(MiscMessage.Subchannel.UPDATE_COMMANDS, result.subchannel());
    }

    // --- rejection: the whole point is that a skewed/corrupt message fails loudly, not silently.

    @Test
    void rejectsAnUnknownProtocolVersion() {
        byte[] bytes = RequestLocationMessage.serialise(LocationActionSubchannel.MOVE, "home");
        bytes[0] = 99; // header byte 0 is the protocol version

        assertThrows(MalformedMessageException.class, () -> RequestLocationMessage.read(bytes));
    }

    @Test
    void rejectsAnUnknownOpcode() {
        byte[] bytes = RequestLocationMessage.serialise(LocationActionSubchannel.MOVE, "home");
        bytes[1] = 99; // header byte 1 is the opcode

        assertThrows(MalformedMessageException.class, () -> RequestLocationMessage.read(bytes));
    }

    @Test
    void rejectsATruncatedBody() {
        byte[] bytes = PlayerLocationMessage.serialise(LocationActionSubchannel.MOVE, LOC, "spawn");
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 4); // drop bytes mid-field

        assertThrows(MalformedMessageException.class, () -> PlayerLocationMessage.read(truncated));
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] bytes = RequestLocationMessage.serialise(LocationActionSubchannel.MOVE, "home");
        byte[] padded = Arrays.copyOf(bytes, bytes.length + 1); // one extra byte the sender never wrote

        assertThrows(MalformedMessageException.class, () -> RequestLocationMessage.read(padded));
    }

    @Test
    void rejectsAMessageWithNoHeaderAtAll() {
        assertThrows(MalformedMessageException.class, () -> MiscMessage.read(new byte[0]));
    }
}
