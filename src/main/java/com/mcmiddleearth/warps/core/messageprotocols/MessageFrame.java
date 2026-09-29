package com.mcmiddleearth.warps.core.messageprotocols;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * Shared framing for every warp plugin message: a one-byte protocol version + one-byte opcode header,
 * strict length-bounded reads, and a trailing-byte check.
 * <p>
 * <b>Contract (root cause #2).</b> The proxy and the Paper backend ship in the same jar but are
 * deployed to separate processes independently, so a version skew between them is a normal
 * operational state - not an edge case. Every message therefore leads with {@link #PROTOCOL_VERSION};
 * a receiver on a different version rejects the whole message ({@link MalformedMessageException})
 * instead of mis-reading a changed field layout (which previously risked teleporting a player to
 * garbage coordinates). <b>Bump {@link #PROTOCOL_VERSION} on ANY change to ANY message's layout.</b>
 * <p>
 * Uses plain JDK {@link DataInputStream}/{@link DataOutputStream} (not guava) so underflow surfaces
 * as a checked {@link EOFException} we can map to a clean rejection, and so the wire path carries no
 * shaded dependency that could itself differ between the two jars.
 */
public final class MessageFrame {

    private MessageFrame() {}

    /** Wire-format version. Proxy and backend must agree. Increment on any message-layout change. */
    public static final byte PROTOCOL_VERSION = 1;

    /** Writes a message body after the shared {@code [version][opcode]} header. */
    @FunctionalInterface
    public interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    /** Decodes a message body, given the opcode read from the header. */
    @FunctionalInterface
    public interface Decoder<T> {
        T decode(int opcode, DataInputStream in) throws IOException, MalformedMessageException;
    }

    /** Serialises {@code [version][opcode][body...]} to a byte array. */
    public static byte[] write(int opcode, Body body) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(PROTOCOL_VERSION);
            out.writeByte(opcode);
            body.write(out);
        } catch (IOException e) {
            // A ByteArrayOutputStream never performs real I/O, so this cannot happen in practice.
            throw new IllegalStateException("in-memory message serialisation failed", e);
        }
        return buffer.toByteArray();
    }

    /**
     * Validates the header, hands the opcode and a bounded reader to {@code decoder}, and verifies the
     * body consumed the message exactly (no truncation, no trailing bytes). Any deviation is a
     * {@link MalformedMessageException} - the caller must drop the message.
     */
    public static <T> T read(byte[] data, Decoder<T> decoder) throws MalformedMessageException {
        ByteArrayInputStream buffer = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(buffer);

        int version;
        int opcode;
        try {
            version = in.readUnsignedByte();
            opcode = in.readUnsignedByte();
        } catch (EOFException e) {
            throw new MalformedMessageException(
                "message too short to contain a header (" + data.length + " byte(s))", e);
        } catch (IOException e) {
            throw new MalformedMessageException("failed to read message header", e);
        }

        if (version != PROTOCOL_VERSION) {
            throw new MalformedMessageException(
                "protocol version mismatch: got " + version + ", expected " + PROTOCOL_VERSION
                + " - the proxy and backend warp jars are skewed, redeploy both");
        }

        T result;
        try {
            result = decoder.decode(opcode, in);
        } catch (EOFException e) {
            throw new MalformedMessageException("message body is truncated (opcode " + opcode + ")", e);
        } catch (IOException e) {
            throw new MalformedMessageException("failed to decode message body (opcode " + opcode + ")", e);
        }

        int trailing = buffer.available();
        if (trailing != 0) {
            throw new MalformedMessageException(
                "message has " + trailing + " unexpected trailing byte(s) (opcode " + opcode + ")");
        }
        return result;
    }
}
