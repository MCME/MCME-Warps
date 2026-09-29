package com.mcmiddleearth.warps.core.messageprotocols;

public class RequestLocationMessage {

    public record Result(LocationActionSubchannel subchannel, String warpName) {}

    public static byte[] serialise(LocationActionSubchannel subchannel, String warpName) {
        return MessageFrame.write(subchannel.code(), out -> out.writeUTF(warpName));
    }

    public static Result read(byte[] bytes) throws MalformedMessageException {
        return MessageFrame.read(bytes, (opcode, in) -> {
            LocationActionSubchannel subchannel = LocationActionSubchannel.fromCode(opcode);
            String warpName = in.readUTF();
            return new Result(subchannel, warpName);
        });
    }
}
