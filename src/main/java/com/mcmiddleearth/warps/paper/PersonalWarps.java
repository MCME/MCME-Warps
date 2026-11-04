package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.messageprotocols.WarpListMessage;
import com.mcmiddleearth.warps.paper.api.WarpListService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The {@link WarpListService}: sends the proxy a request through the player and completes everyone waiting
 * for that player when the list comes back. Requests for a player already waiting share the one answer.
 */
public final class PersonalWarps implements WarpListService, Listener {

    private static final long TIMEOUT_SECONDS = 5;

    private final Plugin plugin;
    private final Map<UUID, List<CompletableFuture<List<PersonalWarp>>>> waiting = new ConcurrentHashMap<>();

    public PersonalWarps(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean available(Player player) {
        return player.getListeningPluginChannels().contains(Channels.WARP_LIST);
    }

    @Override
    public void warp(Player player, String name) {
        byte[] message = WarpListMessage.warp(name);
        if (available(player)) {
            player.sendPluginMessage(plugin, Channels.WARP_LIST, message);
        }
    }

    @Override
    public CompletableFuture<List<PersonalWarp>> privateWarps(Player player) {
        CompletableFuture<List<PersonalWarp>> answer = new CompletableFuture<>();
        if (!available(player)) {
            answer.completeExceptionally(new IllegalStateException("no proxy with MCME-Warps' warp list here"));
            return answer;
        }
        boolean first = waiting.compute(player.getUniqueId(), (id, list) -> {
            List<CompletableFuture<List<PersonalWarp>>> l = list == null ? new ArrayList<>() : list;
            l.add(answer);
            return l;
        }).size() == 1;
        if (first) {
            player.sendPluginMessage(plugin, Channels.WARP_LIST, WarpListMessage.request());
        }
        // Timed out on the main thread like the answer, and dropped from the waiting list.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!answer.isDone()) {
                waiting.computeIfPresent(player.getUniqueId(), (id, l) -> {
                    l.remove(answer);
                    return l.isEmpty() ? null : l;
                });
                answer.completeExceptionally(new java.util.concurrent.TimeoutException(
                    "the proxy didn't send " + player.getName() + "'s warps"));
            }
        }, TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS) / 50);
        return answer;
    }

    /** The proxy's answer, on the main thread (plugin messages arrive there). */
    public void received(Player player, List<WarpListMessage.Entry> entries) {
        List<PersonalWarp> warps = entries.stream()
            .map(e -> new PersonalWarp(e.name(), e.server(), e.location().world(),
                e.location().x(), e.location().y(), e.location().z()))
            .toList();
        List<CompletableFuture<List<PersonalWarp>>> answers = waiting.remove(player.getUniqueId());
        if (answers != null) {
            answers.forEach(a -> a.complete(warps));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        List<CompletableFuture<List<PersonalWarp>>> answers = waiting.remove(event.getPlayer().getUniqueId());
        if (answers != null) {
            answers.forEach(a -> a.cancel(false));
        }
    }
}
