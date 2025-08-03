package com.mcmiddleearth.warps.paper.listener;

import com.mcmiddleearth.warps.core.Channels;
import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.core.messageprotocols.PlayerLocationMessage;
import com.mcmiddleearth.warps.core.messageprotocols.RequestLocationMessage;
import com.mcmiddleearth.warps.core.messageprotocols.TeleportMessage;
import com.mcmiddleearth.warps.core.messageprotocols.TeleportResult;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class MessageListener implements PluginMessageListener {

    private static final int MAX_RADIUS = 15;
    private final Plugin plugin;

    public MessageListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] bytes) {
        switch (channel) {
            case Channels.WARP -> handleWarpRequest(player, bytes);
            case Channels.PLAYER_LOCATION -> handleLocationRequest(player, bytes);
            default -> plugin.getComponentLogger().warn(Component.text("Channel '" + channel + "' has no handler!"));
        }
    }

    private void handleLocationRequest(Player player, byte[] bytes) {
        RequestLocationMessage.Result data = RequestLocationMessage.read(bytes);
        SimpleLocation location = new SimpleLocation(
            player.getWorld().getName(),
            player.getX(), player.getY(), player.getZ(),
            player.getYaw(), player.getPitch()
        );

        player.sendPluginMessage(
            plugin,
            Channels.PLAYER_LOCATION,
            PlayerLocationMessage.serialise(data.subchannel(), location, data.warpName())
        );

    }

    private void handleWarpRequest(Player player, byte[] bytes) {
        var result = TeleportMessage.read(bytes);
        String warpName = result.warpName();
        SimpleLocation data = result.data();

        World world = Bukkit.getWorld(data.world());
        if (world == null) {
            player.sendRichMessage("<red>Unable to perform the teleport, no world exists with name " + data.world());
            return;
        }

        Location baseWarpLocation = new Location(world, data.x(), data.y(), data.z(), data.yaw(), data.pitch());
        Optional<Location> safeLocationOpt = getSafeWarpLocation(baseWarpLocation);

        if (safeLocationOpt.isEmpty()) {
            player.sendRichMessage("<red>\"%s\" is not safe. Please notify a member of staff.".formatted(warpName));
            return;
        }

        Location finalWarpLocation = safeLocationOpt.get();

        // Avoid redundant teleport and avoid a success message (which increments the warp counter)
        if (player.getLocation().equals(finalWarpLocation)) {
            // Only notify the player if the warp is on the same server as them
            // (since their previous position on the newly connected server could be the same as warpLocation)
            if (result.subchannel().equals(TeleportMessage.Subchannel.TELEPORT_SAME_SERVER)) {
                player.sendRichMessage("<gray>You are already at warp " + warpName);
            }
            return;
        }

        player.teleportAsync(finalWarpLocation).thenAccept(success -> {
            if (success) {
                player.sendPluginMessage(
                    plugin,
                    Channels.WARP,
                    TeleportResult.serialise(warpName, TeleportResult.ResultType.SUCCESS)
                );

                boolean wasBaseUnsafe = !baseWarpLocation.equals(finalWarpLocation);
                if (wasBaseUnsafe) {
                    player.sendRichMessage("<gray>\"%s\" is not safe. You were placed nearby".formatted(warpName));
                }

            }
        });
    }

    private Optional<Location> getSafeWarpLocation(Location base) {
        Material feet = base.getBlock().getType();
        Material head = base.clone().add(0, 1, 0).getBlock().getType();

        if (feet == Material.AIR && head == Material.AIR) {
            return Optional.of(base);
        }

        World world = base.getWorld();
        int baseX = base.getBlockX();
        int baseY = base.getBlockY();
        int baseZ = base.getBlockZ();

        for (int radius = 1; radius <= MAX_RADIUS; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {

                        // Only check the outer shell of the cube
                        if (Math.abs(dx) != radius && Math.abs(dy) != radius && Math.abs(dz) != radius) {
                            continue;
                        }

                        int x = baseX + dx;
                        int y = baseY + dy;
                        int z = baseZ + dz;

                        // Skip positions outside world height limits
                        if (y < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
                            continue;
                        }

                        Block candidateFeet = world.getBlockAt(x, y, z);
                        Block candidateHead = world.getBlockAt(x, y + 1, z);

                        if (candidateFeet.getType() == Material.AIR && candidateHead.getType() == Material.AIR) {
                            return Optional.of(new Location(world, x + 0.5, y, z + 0.5));
                        }
                    }
                }
            }
        }

        // No safe block found
        return Optional.empty();
    }
}