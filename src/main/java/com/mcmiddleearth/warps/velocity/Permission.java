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
    EDIT_PUBLIC_WARPS("mcmewarps.cmd.edit-public-warps");

    // TODO:
    // - mcmewarps.limit.ignore -> private limit ignore

    // MyWarp Permissions:
    // plugin.yml -> https://github.com/MyWarp/MyWarp/blob/ef0ef6ddb18c2c86a4d7223de7fa7fb73ac67984/mywarp-bukkit/src/main/resources/plugin.yml#L64

    private final String permissionNode;

    Permission(String permissionNode) {
        this.permissionNode = permissionNode;
    }

    public String getNode() {
        return permissionNode;
    }
}