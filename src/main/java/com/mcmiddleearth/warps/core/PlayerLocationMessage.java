package com.mcmiddleearth.warps.core;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class PlayerLocationMessage {

    public record Result(CreateSubchannels subchannel, SimpleLocation warpLocation, String warpName) {}

    public static byte[] serialise(CreateSubchannels subchannel, SimpleLocation data, String warpName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(subchannel.name());
        out.writeUTF(data.world());
        out.writeDouble(data.x());
        out.writeDouble(data.y());
        out.writeDouble(data.z());
        out.writeFloat(data.yaw());
        out.writeFloat(data.pitch());
        out.writeUTF(warpName);

        return out.toByteArray();
    }

    public static Result read(byte[] bytes) {
        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);

        String strSubchannel = in.readUTF();
        CreateSubchannels subchannel = CreateSubchannels.valueOf(strSubchannel);

        String world = in.readUTF();
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        float yaw  = in.readFloat();
        float pitch = in.readFloat();
        SimpleLocation data = new SimpleLocation(world, x, y, z, yaw, pitch);
        String warpName = in.readUTF();

        return new Result(subchannel, data, warpName);
    }
}
