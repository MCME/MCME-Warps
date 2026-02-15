package com.mcmiddleearth.warps.velocity;

public enum Permission {
    WARP("mcmewarps.cmd.warp"),
    LOCAL_WARP("mcmewarps.cmd.local-warp"),
    RANDOM("mcmewarps.cmd.random"),
    CREATE_PRIVATE("mcmewarps.cmd.create-private"),
    CREATE_PUBLIC("mcmewarps.cmd.create-public"),
    DELETE("mcmewarps.cmd.delete"),
    RENAME("mcmewarps.cmd.rename"),
    MOVE("mcmewarps.cmd.move"),
    MANAGE_MEMBERS("mcmewarps.cmd.manage-members"),
    SET_PUBLIC("mcmewarps.cmd.set-public"),
    SET_PRIVATE("mcmewarps.cmd.set-private"),
    RELOAD("mcmewarps.cmd.reload"),
    WELCOME("mcmewarps.cmd.welcome-message"),
    SET_ICON("mcmewarps.cmd.set-icon"),
    SET_LAYER("mcmewarps.cmd.set-layer"),
    LIST("mcmewarps.cmd.list"),

    OVERRIDE_USE("mcmewarps.override.use"),
    OVERRIDE_MODIFY("mcmewarps.override.modify"),
    IGNORE_PRIVATE_WARPS_LIMIT("mcmewarps.limits.ignore.private");

    private final String permissionNode;

    Permission(String permissionNode) {
        this.permissionNode = permissionNode;
    }

    public String getNode() {
        return permissionNode;
    }
}