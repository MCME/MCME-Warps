package com.mcmiddleearth.warps.core;

public enum WarpIcon {
    // Full list: https://github.com/webbukkit/dynmap/wiki/Using-Markers#marker-icons
    DEFAULT("greenflag"),
    WIP("construction"),
    ON_HOLD("yellowflag"),
    NOT_STARTED("redflag"),
    PIRATE("pirateflag");

    private final String value;

    WarpIcon(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}