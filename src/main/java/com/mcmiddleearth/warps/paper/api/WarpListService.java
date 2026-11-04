package com.mcmiddleearth.warps.paper.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A player's own private warps, for other plugins on this backend (a map, a warp book): get it from Bukkit's
 * services manager. The backend only holds public warps, so this asks the proxy, through the player's
 * connection, over {@code mcme:warp-list}.
 */
public interface WarpListService {

    /**
     * The player's private warps on the whole network, sorted by name. Completes on the main thread; fails
     * (with a {@link java.util.concurrent.TimeoutException}) if the proxy doesn't answer within a few seconds,
     * for example when it runs an older MCME-Warps.
     */
    CompletableFuture<List<PersonalWarp>> privateWarps(Player player);

    /**
     * One private warp. {@code /warp <name>} from its owner goes there, on whichever server it is.
     *
     * @param server the network server it is on
     */
    record PersonalWarp(String name, String server, String world, double x, double y, double z) {}
}
