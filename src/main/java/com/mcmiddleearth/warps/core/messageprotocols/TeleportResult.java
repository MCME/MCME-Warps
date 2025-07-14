package com.mcmiddleearth.warps.core.messageprotocols;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

// paper uses this protocol to send the result of a teleport to the velocity proxy
public class TeleportResult {
    public enum Subchannel {
        TELEPORT_RESULT
    }

    public enum ResultType {
        SUCCESS
    }

    public record Response(Subchannel subchannel, String warpName, ResultType resultType) {}

    public static byte[] serialise(String warpName, ResultType resultType) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(Subchannel.TELEPORT_RESULT.name());
        out.writeUTF(warpName);
        out.writeUTF(resultType.name());

        return out.toByteArray();
    }

    public static Response read(byte[] bytes) {
        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);

        String strSubchannel = in.readUTF();
        Subchannel subchannel = Subchannel.valueOf(strSubchannel);

        String warpName = in.readUTF();

        String strResultType = in.readUTF();
        ResultType resultType = ResultType.valueOf(strResultType);

        return new Response(subchannel, warpName, resultType);
    }
}
