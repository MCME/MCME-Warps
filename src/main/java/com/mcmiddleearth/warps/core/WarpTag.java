package com.mcmiddleearth.warps.core;

public enum WarpTag {
    DEFAULT("greenflag"),
    WIP("construction");

    private final String value;

    WarpTag(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}