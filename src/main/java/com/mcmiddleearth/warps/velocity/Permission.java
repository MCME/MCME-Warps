package com.mcmiddleearth.warps.velocity;

public enum Permission {
    WARP("mcmewarps.cmd.warp"),
    RANDOM("mcmewarps.cmd.random"),
    CREATE_PRIVATE("mcmewarps.cmd.create-private"),
    CREATE_PUBLIC("mcmewarps.cmd.create-public"),
    DELETE("mcmewarps.cmd.delete"),
    RENAME("mcmewarps.cmd.rename"),
    MOVE("mcmewarps.cmd.move"),
    ADD_MEMBER("mcmewarps.cmd.add-member"),
    REMOVE_MEMBER("mcmewarps.cmd.remove-member"),
    SET_PUBLIC("mcmewarps.cmd.set-public"),
    SET_PRIVATE("mcmewarps.cmd.set-private"),
    RELOAD("mcmewarps.cmd.reload"),
    WELCOME("mcmewarps.cmd.welcome-message"),
    TAG("mcmewarps.cmd.tag"),

    EDIT_PUBLIC_WARPS("mcmewarps.modify.public-warps"),
    IGNORE_PRIVATE_WARPS_LIMIT("mcmewarps.limits.ignore.private");

    private final String permissionNode;

    Permission(String permissionNode) {
        this.permissionNode = permissionNode;
    }

    public String getNode() {
        return permissionNode;
    }
}