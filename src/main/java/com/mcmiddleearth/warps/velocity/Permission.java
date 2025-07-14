package com.mcmiddleearth.warps.velocity;

public enum Permission {
    WARP("mcmewarps.cmd.warp"),
    RANDOM("mcmewarps.cmd.random"),
    CREATE_PRIVATE("mcmewarps.cmd.create-private"),
    CREATE_PUBLIC("mcmewarps.cmd.create-public"),
    DELETE("mcmewarps.cmd.delete"),
    RENAME("mcmewarps.cmd.rename"),
    MOVE("mcmewarps.cmd.move"),
    INVITE("mcmewarps.cmd.invite"),
    UNINVITE("mcmewarps.cmd.uninvite"),
    MAKE_PUBLIC("mcmewarps.cmd.make-public"),
    MAKE_PRIVATE("mcmewarps.cmd.make-private"),
    RELOAD("mcmewarps.cmd.reload"),
    WELCOME("mcmewarps.cmd.welcome-message"),

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