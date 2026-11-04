package com.mcmiddleearth.warps.core.messageprotocols;

import com.mcmiddleearth.warps.core.SimpleLocation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A player's own private warps, for a map on the backend (channel {@code mcme:warp-list}), and warping the player
 * from there. The backend can't read private warps itself: only each server's public warps are shared with it, by
 * symlink. Nor can it run {@code /warp}, a proxy command: a backend's commands never reach the proxy.
 * <ul>
 * <li>{@link Subchannel#REQUEST}, backend to proxy: no body. It is about the player whose connection it comes
 * through, so a backend can only ask for the warps of a player it hosts.</li>
 * <li>{@link Subchannel#LIST}, proxy to backend: the warps, each with the server it is on. At most
 * {@link #MAX_BYTES} in all (and so never more than {@link #MAX_WARPS}).</li>
 * <li>{@link Subchannel#WARP}, backend to proxy: a warp's name. The proxy runs {@code /warp <name>} as the player
 * whose connection it comes through, with that command's checks, as if they had typed it.</li>
 * </ul>
 * A new channel and opcodes: no existing message's layout changes, and a backend without this channel never
 * sends a request, so {@link MessageFrame#PROTOCOL_VERSION} stays as it is.
 */
public final class WarpListMessage {

    /** The most warps a list may claim; a reader rejects a larger count. */
    public static final int MAX_WARPS = 1000;

    /**
     * The most bytes a whole list message may take. The proxy sends it to the backend as a serverbound
     * plugin message, and the backend (vanilla and Paper) rejects those over 32767 bytes, which would
     * disconnect the player. An entry takes at least 46 bytes, so this cap is always reached before
     * {@link #MAX_WARPS}.
     */
    public static final int MAX_BYTES = 32767;

    /** The frame header (version, opcode) and the {@code u16} count. */
    private static final int LIST_OVERHEAD = 4;

    private WarpListMessage() {}

    public enum Subchannel {
        REQUEST(1),
        LIST(2),
        WARP(3);

        private final int code;

        Subchannel(int code) {
            this.code = code;
        }

        /** The stable wire opcode. Never reuse or renumber - two skewed jars must still agree. */
        public int code() {
            return code;
        }

        public static Subchannel fromCode(int code) throws MalformedMessageException {
            for (Subchannel value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MalformedMessageException("unknown WarpListMessage.Subchannel opcode: " + code);
        }
    }

    /** One warp in a list: its name, the server it is on and where. */
    public record Entry(String name, String server, SimpleLocation location) {}

    /** The longest warp name a WARP message may carry. */
    public static final int MAX_NAME = 256;

    /** A read message: a request (no entries, no name), a list (no name) or a warp (its name, no entries). */
    public record Result(Subchannel subchannel, List<Entry> warps, String name) {}

    public static byte[] request() {
        return MessageFrame.write(Subchannel.REQUEST.code(), out -> {});
    }

    /** Asks the proxy to run {@code /warp <name>} as the player. */
    public static byte[] warp(String name) {
        if (!validName(name)) {
            throw new IllegalArgumentException("not a warp name: " + name);
        }
        return MessageFrame.write(Subchannel.WARP.code(), out -> out.writeUTF(name));
    }

    /** One line of 1 to MAX_NAME characters, none of them control characters, not starting with a space. */
    public static boolean validName(String name) {
        return name != null && !name.isEmpty() && name.length() <= MAX_NAME && !name.startsWith(" ")
            && name.chars().noneMatch(Character::isISOControl);
    }

    /**
     * The list: the warps in order, as many as fit in {@link #MAX_BYTES}. The rest are left out, as is any
     * warp whose strings can't be encoded.
     */
    public static byte[] list(List<Entry> warps) {
        List<byte[]> sent = new ArrayList<>();
        int size = LIST_OVERHEAD;
        for (Entry e : warps) {
            if (sent.size() == MAX_WARPS) {
                break;
            }
            byte[] encoded = encode(e);
            if (encoded == null) {
                continue;
            }
            if (size + encoded.length > MAX_BYTES) {
                break;
            }
            sent.add(encoded);
            size += encoded.length;
        }
        return MessageFrame.write(Subchannel.LIST.code(), out -> {
            out.writeShort(sent.size());
            for (byte[] encoded : sent) {
                out.write(encoded);
            }
        });
    }

    /** One entry's bytes, or null if a string is too long for {@code writeUTF}. */
    private static byte[] encode(Entry e) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeUTF(e.name());
            out.writeUTF(e.server());
            SimpleLocation l = e.location();
            out.writeUTF(l.world());
            out.writeDouble(l.x());
            out.writeDouble(l.y());
            out.writeDouble(l.z());
            out.writeFloat(l.yaw());
            out.writeFloat(l.pitch());
        } catch (IOException ex) {
            return null;
        }
        return buffer.toByteArray();
    }

    public static Result read(byte[] bytes) throws MalformedMessageException {
        return MessageFrame.read(bytes, (opcode, in) -> {
            Subchannel subchannel = Subchannel.fromCode(opcode);
            if (subchannel == Subchannel.REQUEST) {
                return new Result(subchannel, List.of(), null);
            }
            if (subchannel == Subchannel.WARP) {
                String name = in.readUTF();
                if (!validName(name)) {
                    throw new MalformedMessageException("not a warp name (" + name.length() + " characters)");
                }
                return new Result(subchannel, List.of(), name);
            }
            int count = in.readUnsignedShort();
            if (count > MAX_WARPS) {
                throw new MalformedMessageException("warp list of " + count + " warps, more than " + MAX_WARPS);
            }
            List<Entry> warps = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String name = in.readUTF();
                String server = in.readUTF();
                SimpleLocation location = new SimpleLocation(in.readUTF(), in.readDouble(), in.readDouble(),
                    in.readDouble(), in.readFloat(), in.readFloat());
                warps.add(new Entry(name, server, location));
            }
            return new Result(subchannel, List.copyOf(warps), null);
        });
    }
}
