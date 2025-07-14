package com.mcmiddleearth.warps.core.messageprotocols;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mcmiddleearth.warps.core.SimpleLocation;

public class TeleportMessage {

    public enum Subchannel {
        TELEPORT_SAME_SERVER,
        TELEPORT_NEW_SERVER
    }

    public record Result(Subchannel subchannel, String warpName, SimpleLocation data) {}

    public static byte[] serialise(Subchannel subchannel, String warpName, SimpleLocation data) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(subchannel.name());
        out.writeUTF(warpName);

        out.writeUTF(data.world());
        out.writeDouble(data.x());
        out.writeDouble(data.y());
        out.writeDouble(data.z());
        out.writeFloat(data.yaw());
        out.writeFloat(data.pitch());

        return out.toByteArray();
    }

    public static Result read(byte[] bytes) {
        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);

        String strSubchannel = in.readUTF();
        Subchannel subchannel = Subchannel.valueOf(strSubchannel);

        String warpName = in.readUTF();

        String world = in.readUTF();
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        float yaw  = in.readFloat();
        float pitch = in.readFloat();
        SimpleLocation data = new SimpleLocation(world, x, y, z, yaw, pitch);

        return new Result(subchannel, warpName, data);
    }
}
