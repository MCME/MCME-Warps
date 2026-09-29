package com.mcmiddleearth.warps.core.messageprotocols;

public class MiscMessage {

    public enum Subchannel {
        UPDATE_COMMANDS(1);

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
            throw new MalformedMessageException("unknown MiscMessage.Subchannel opcode: " + code);
        }
    }

    public record Result(Subchannel subchannel) {}

    public static byte[] serialise(Subchannel subchannel) {
        // Header-only message: the opcode carries all the information.
        return MessageFrame.write(subchannel.code(), out -> {});
    }

    public static Result read(byte[] bytes) throws MalformedMessageException {
        return MessageFrame.read(bytes, (opcode, in) -> new Result(Subchannel.fromCode(opcode)));
    }
}
