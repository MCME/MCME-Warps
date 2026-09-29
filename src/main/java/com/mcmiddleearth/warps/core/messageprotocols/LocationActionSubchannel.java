package com.mcmiddleearth.warps.core.messageprotocols;

public enum LocationActionSubchannel {
    CREATE_PRIVATE(1),
    CREATE_PUBLIC(2),
    MOVE(3);

    private final int code;

    LocationActionSubchannel(int code) {
        this.code = code;
    }

    /** The stable wire opcode. Never reuse or renumber - two skewed jars must still agree. */
    public int code() {
        return code;
    }

    public static LocationActionSubchannel fromCode(int code) throws MalformedMessageException {
        for (LocationActionSubchannel value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new MalformedMessageException("unknown LocationActionSubchannel opcode: " + code);
    }
}
