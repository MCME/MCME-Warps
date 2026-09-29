package com.mcmiddleearth.warps;

public enum Permission {
    CREATE("mcmewarps.command.create"),
    TEST("mcmewarps.command.test");

    private final String permissionNode;

    Permission(String permissionNode) {
        this.permissionNode = permissionNode;
    }

    public String getNode() {
        return permissionNode;
    }
}
