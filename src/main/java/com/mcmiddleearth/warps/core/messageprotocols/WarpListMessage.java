package com.mcmiddleearth.warps.core.messageprotocols;

import com.mcmiddleearth.warps.core.SimpleLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's own private warps, for a map on the backend (channel {@code mcme:warp-list}). The backend can't
 * read private warps itself: only each server's public warps are shared with it, by symlink.
 * <ul>
 * <li>{@link Subchannel#REQUEST}, backend to proxy: no body. It is about the player whose connection it comes
 * through, so a backend can only ask for the warps of a player it hosts.</li>
 * <li>{@link Subchannel#LIST}, proxy to backend: the warps, each with the server it is on. At most
 * {@link #MAX_WARPS}.</li>
 * </ul>
 * A new channel and opcodes: no existing message's layout changes, and a backend without this channel never
 * sends a request, so {@link MessageFrame#PROTOCOL_VERSION} stays as it is.
 */
public final class WarpListMessage {

    /** The most warps one list carries, which keeps a message well under the plugin-message size limit. */
    public static final int MAX_WARPS = 1000;

    private WarpListMessage() {}

    public enum Subchannel {
        REQUEST(1),
        LIST(2);

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

    /** A read message: a request (no entries) or a list. */
    public record Result(Subchannel subchannel, List<Entry> warps) {}

    public static byte[] request() {
        return MessageFrame.write(Subchannel.REQUEST.code(), out -> {});
    }

    /** The list; only the first {@link #MAX_WARPS} warps go. */
    public static byte[] list(List<Entry> warps) {
        List<Entry> sent = warps.size() > MAX_WARPS ? warps.subList(0, MAX_WARPS) : warps;
        return MessageFrame.write(Subchannel.LIST.code(), out -> {
            out.writeShort(sent.size());
            for (Entry e : sent) {
                out.writeUTF(e.name());
                out.writeUTF(e.server());
                SimpleLocation l = e.location();
                out.writeUTF(l.world());
                out.writeDouble(l.x());
                out.writeDouble(l.y());
                out.writeDouble(l.z());
                out.writeFloat(l.yaw());
                out.writeFloat(l.pitch());
            }
        });
    }

    public static Result read(byte[] bytes) throws MalformedMessageException {
        return MessageFrame.read(bytes, (opcode, in) -> {
            Subchannel subchannel = Subchannel.fromCode(opcode);
            if (subchannel == Subchannel.REQUEST) {
                return new Result(subchannel, List.of());
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
            return new Result(subchannel, List.copyOf(warps));
        });
    }
}
