package com.mcmiddleearth.warps.core;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class RequestLocationMessage {

    public record Result(CreateSubchannels subchannel, String warpName) {}

    public static byte[] serialise(CreateSubchannels subchannel, String warpName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(subchannel.name());
        out.writeUTF(warpName);
        return out.toByteArray();
    }

    public static Result read(byte[] bytes) {
        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);

        CreateSubchannels subchannel = CreateSubchannels.valueOf(in.readUTF());
        String warpName = in.readUTF();

        return new Result(subchannel, warpName);
    }
}
