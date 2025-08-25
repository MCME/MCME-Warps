package com.mcmiddleearth.warps.core.messageprotocols;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class MiscMessage {

    public enum Subchannel {
        UPDATE_COMMANDS,
    }

    public record Result(Subchannel subchannel) {}

    public static byte[] serialise(Subchannel subchannel) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(subchannel.name());
        return out.toByteArray();
    }

    public static Result read(byte[] bytes) {
        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);

        Subchannel subchannel = Subchannel.valueOf(in.readUTF());

        return new Result(subchannel);
    }
}
