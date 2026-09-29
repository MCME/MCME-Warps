package com.mcmiddleearth.warps.core.messageprotocols;

import com.mcmiddleearth.warps.core.SimpleLocation;

public class TeleportMessage {

    public enum Subchannel {
        SAME_SERVER(1),
        DIFF_SERVER(2),
        LOCAL_WARP(3);

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
            throw new MalformedMessageException("unknown TeleportMessage.Subchannel opcode: " + code);
        }
    }

    public record Result(Subchannel subchannel, String warpName, SimpleLocation data) {}

    public static byte[] serialise(Subchannel subchannel, String warpName, SimpleLocation data) {
        return MessageFrame.write(subchannel.code(), out -> {
            out.writeUTF(warpName);
            out.writeUTF(data.world());
            out.writeDouble(data.x());
            out.writeDouble(data.y());
            out.writeDouble(data.z());
            out.writeFloat(data.yaw());
            out.writeFloat(data.pitch());
        });
    }

    public static Result read(byte[] bytes) throws MalformedMessageException {
        return MessageFrame.read(bytes, (opcode, in) -> {
            Subchannel subchannel = Subchannel.fromCode(opcode);
            String warpName = in.readUTF();
            String world = in.readUTF();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float pitch = in.readFloat();
            SimpleLocation data = new SimpleLocation(world, x, y, z, yaw, pitch);
            return new Result(subchannel, warpName, data);
        });
    }
}
