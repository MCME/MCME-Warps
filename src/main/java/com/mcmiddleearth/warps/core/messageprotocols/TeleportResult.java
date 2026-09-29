package com.mcmiddleearth.warps.core.messageprotocols;

// paper uses this protocol to send the result of a teleport to the velocity proxy
public class TeleportResult {

    public enum Subchannel {
        TELEPORT_RESULT(1);

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
            throw new MalformedMessageException("unknown TeleportResult.Subchannel opcode: " + code);
        }
    }

    public enum ResultType {
        SUCCESS(1);

        private final int code;

        ResultType(int code) {
            this.code = code;
        }

        /** The stable wire code. Never reuse or renumber - two skewed jars must still agree. */
        public int code() {
            return code;
        }

        public static ResultType fromCode(int code) throws MalformedMessageException {
            for (ResultType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new MalformedMessageException("unknown TeleportResult.ResultType code: " + code);
        }
    }

    public record Response(Subchannel subchannel, String warpName, ResultType resultType) {}

    public static byte[] serialise(String warpName, ResultType resultType) {
        return MessageFrame.write(Subchannel.TELEPORT_RESULT.code(), out -> {
            out.writeUTF(warpName);
            out.writeByte(resultType.code());
        });
    }

    public static Response read(byte[] bytes) throws MalformedMessageException {
        return MessageFrame.read(bytes, (opcode, in) -> {
            Subchannel subchannel = Subchannel.fromCode(opcode);
            String warpName = in.readUTF();
            ResultType resultType = ResultType.fromCode(in.readUnsignedByte());
            return new Response(subchannel, warpName, resultType);
        });
    }
}
