package com.mcmiddleearth.warps.core;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.io.IOException;

public class TeleportMessage {
    public static final String SUBCHANNEL = "TeleportLocation";

    /** The data structure used for teleportation messages */
    public record TeleportData(String world, double x, double y, double z, float yaw, float pitch) {
    }

    // TODO: Rename to build or serialise???
    public static ByteArrayDataOutput write(TeleportData data) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(SUBCHANNEL);
        out.writeUTF(data.world());
        out.writeDouble(data.x());
        out.writeDouble(data.y());
        out.writeDouble(data.z());
        out.writeFloat(data.yaw());
        out.writeFloat(data.pitch());

        return out;
    }
    public static TeleportData read(byte[] bytes) throws IOException {
        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);

        String subchannel = in.readUTF();
        if (!SUBCHANNEL.equals(subchannel)) throw new IOException("Unexpected subchannel: " + subchannel);

        String world = in.readUTF();
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        float yaw  = in.readFloat();
        float pitch = in.readFloat();

        return new TeleportData(world, x, y, z, yaw, pitch);
    }
}
