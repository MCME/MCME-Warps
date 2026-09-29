package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.Utils;

import java.nio.file.Path;

/**
 * Pure helpers for turning a warp into its on-disk location and normalised key.
 * <p>
 * Deliberately free of any dependency on the {@link com.mcmiddleearth.warps.velocity.WarpVelocity}
 * singleton (unlike {@link WarpManager}, whose static state binds to the running proxy) so this
 * logic - which is security-critical - can be unit tested directly.
 */
public final class WarpPaths {

    private WarpPaths() {}

    /** The normalised key a warp is stored and looked up under. */
    public static String normaliseWarpName(String warpName) {
        return Utils.normaliseString(warpName).trim();
    }

    /**
     * Resolves the on-disk path for a warp under {@code warpsDir}.
     * <p>
     * The warp name, server and world all become path segments. A crafted value - e.g. a warp name
     * of {@code ../../..} arriving via a forged plugin message (SEC-1) - could otherwise escape the
     * warps directory and let an attacker write a {@code .yml} anywhere the proxy can write. This
     * method normalises the result and refuses any path that falls outside {@code warpsDir}.
     *
     * @throws IllegalArgumentException if the resolved path would fall outside {@code warpsDir}
     */
    public static Path resolveWarpPath(Path warpsDir, Warp warp) {
        String warpName = normaliseWarpName(warp.getName());

        Path dir = warp.isOfType(Warp.Type.PUBLIC)
            ? warpsDir.resolve(warp.getServer()).resolve(warp.getLocation().world())
            : warpsDir.resolve("private-warps").resolve(warp.getCreatorId().toString());

        Path resolved = dir.resolve(warpName + ".yml").normalize();

        Path base = warpsDir.normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException(
                "Refusing to resolve warp '" + warp.getName()
                    + "' to a path outside the warps directory: " + resolved);
        }

        return resolved;
    }
}
