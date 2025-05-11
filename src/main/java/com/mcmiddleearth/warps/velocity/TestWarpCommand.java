package com.mcmiddleearth.warps.velocity;

import com.google.common.io.ByteArrayDataOutput;
import com.mcmiddleearth.warps.core.TeleportMessage;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

// TODO:
// - Implement the various warp commands
//   - With permissions, suggestions etc.
// - Accessing the relevant warp data based on the warpName argument
// - Use MCME-Base command helpers?
public final class TestWarpCommand {

    public static BrigadierCommand createBrigadierCommand(final ProxyServer proxy) {
        LiteralCommandNode<CommandSource> commandNode = BrigadierCommand.literalArgumentBuilder("test")
            .executes(context -> {
                // TODO: If player check???
                CommandSource source = context.getSource();
                Player sender = (Player)source; // Q: Is there a way without casting???

                final String targetServerName = "factions";
                Optional<RegisteredServer> targetServer = proxy.getServer(targetServerName);

                targetServer.ifPresent(server -> {
                    sender.createConnectionRequest(server).connectWithIndication()
                        // Q: Does this block the main thread?
                        .whenCompleteAsync((success, throwable) -> {
                            // Q: Success vs throw?
                            if (throwable != null) {
                                throwable.printStackTrace();
                                sender.sendMessage(Component.text("Failed to connect: " + throwable.getMessage(), NamedTextColor.RED));
                                return;
                            }

                            // TODO: Inside the Warp class turn the yaml into TeleportData
                            double x = -46.24;
                            double y = 96.0;
                            double z = -16.461;
                            float yaw = -91f;
                            float pitch = 26f;
                            TeleportMessage.TeleportData data = new TeleportMessage.TeleportData("world", x, y, z, yaw, pitch);
//                            ByteArrayDataOutput out = TeleportMessage.serialise(data);

                            // Messaging the backend server, using the sender's connection
                            Optional<ServerConnection> connection = sender.getCurrentServer();
                            connection.ifPresent(serverConnection -> {
                                serverConnection.sendPluginMessage(WarpVelocity.MAIN_ID, TeleportMessage.serialise(data));
                            });

                            if (success) {
                                sender.sendMessage(Component.text("Successfully connected to " + server.getServerInfo().getName() + "!", NamedTextColor.GREEN));
                            } else {
                                sender.sendMessage(Component.text("Connection failed to " + server.getServerInfo().getName(), NamedTextColor.RED));
                            }
                        });
                });

                return Command.SINGLE_SUCCESS;
            })
            .build();

        return new BrigadierCommand(commandNode);
    }
}
