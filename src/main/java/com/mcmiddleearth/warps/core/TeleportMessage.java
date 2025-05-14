package com.mcmiddleearth.warps.core;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

// TODO: Rename?
public class TeleportMessage {

    public enum Subchannel {
        TELEPORT
    }

    // TODO: Rename to Output, Result?
    public record TeleportResult(Subchannel subchannel, SimpleLocation data) {
    }

    // Q: Remove subchannel argument?
    public static byte[] serialise(Subchannel subchannel, SimpleLocation data) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(subchannel.name());
        out.writeUTF(data.world());
        out.writeDouble(data.x());
        out.writeDouble(data.y());
        out.writeDouble(data.z());
        out.writeFloat(data.yaw());
        out.writeFloat(data.pitch());

        return out.toByteArray();
    }

    public static TeleportResult read(byte[] bytes) {
        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);

        String strSubchannel = in.readUTF();
        Subchannel subchannel = Subchannel.valueOf(strSubchannel);

        String world = in.readUTF();
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        float yaw  = in.readFloat();
        float pitch = in.readFloat();
        SimpleLocation data = new SimpleLocation(world, x, y, z, yaw, pitch);

        return new TeleportResult(subchannel, data);
    }
}
