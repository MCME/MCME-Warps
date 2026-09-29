package com.mcmiddleearth.warps.core.messageprotocols;

import com.mcmiddleearth.warps.core.SimpleLocation;

public class PlayerLocationMessage {

    public record Result(LocationActionSubchannel subchannel, SimpleLocation warpLocation, String warpName) {}

    public static byte[] serialise(LocationActionSubchannel subchannel, SimpleLocation data, String warpName) {
        return MessageFrame.write(subchannel.code(), out -> {
            out.writeUTF(data.world());
            out.writeDouble(data.x());
            out.writeDouble(data.y());
            out.writeDouble(data.z());
            out.writeFloat(data.yaw());
            out.writeFloat(data.pitch());
            out.writeUTF(warpName);
        });
    }

    public static Result read(byte[] bytes) throws MalformedMessageException {
        return MessageFrame.read(bytes, (opcode, in) -> {
            LocationActionSubchannel subchannel = LocationActionSubchannel.fromCode(opcode);
            String world = in.readUTF();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float pitch = in.readFloat();
            SimpleLocation data = new SimpleLocation(world, x, y, z, yaw, pitch);
            String warpName = in.readUTF();
            return new Result(subchannel, data, warpName);
        });
    }
}
