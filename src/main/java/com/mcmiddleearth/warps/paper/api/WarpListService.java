package com.mcmiddleearth.warps.paper.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A player's own private warps, and warping, for other plugins on this backend (a map, a warp book): get it from
 * Bukkit's services manager. The backend only holds public warps and can't run the proxy's {@code /warp}, so this
 * asks the proxy, through the player's connection, over {@code mcme:warp-list}.
 */
public interface WarpListService {

    /**
     * The player's private warps on the whole network, sorted by name. Completes on the main thread; fails
     * (with a {@link java.util.concurrent.TimeoutException}) if the proxy doesn't answer within a few seconds,
     * for example when it runs an older MCME-Warps.
     */
    CompletableFuture<List<PersonalWarp>> privateWarps(Player player);

    /**
     * Whether the proxy the player is connected through takes these requests: false on a server without a proxy,
     * or behind one with an older MCME-Warps (which doesn't register the channel).
     */
    boolean available(Player player);

    /**
     * Warps the player as {@code /warp <name>} typed by them would, on whichever server the warp is: the proxy
     * runs that command for them, with its checks and messages. Does nothing when not {@link #available}.
     *
     * @throws IllegalArgumentException if the name can't be a warp's (empty, too long, a control character)
     */
    void warp(Player player, String name);

    /**
     * One private warp. {@code /warp <name>} from its owner goes there, on whichever server it is.
     *
     * @param server the network server it is on
     */
    record PersonalWarp(String name, String server, String world, double x, double y, double z) {}
}
