package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Optional;
import java.util.function.Consumer;

public final class ServerConnectUtils {

    private ServerConnectUtils() { }

    /**
     * Attempts to connect a player to a target server.
     * Handles errors and sends messages automatically.
     *
     * @param sender The player to connect
     * @param targetServerName Name of the target server
     * @param callback Runs on successful connection (provides the player's connection)
     */
    public static void connectPlayerToServer(
        Player sender,
        String targetServerName,
        Consumer<ServerConnection> callback
    ) {
        ProxyServer proxy = WarpVelocity.getProxy();
        Optional<RegisteredServer> optTargetServer = proxy.getServer(targetServerName);

        if (optTargetServer.isEmpty()) {
            sender.sendRichMessage(
                "<red>Server '<server>' not found!",
                Placeholder.unparsed("server", targetServerName)
            );
            // Logged as well as messaged: a warp naming a server that isn't in velocity.toml is a
            // permanent data defect, not a transient outage. Without this it is invisible to
            // operators and only ever surfaces as one player's confusion.
            WarpVelocity.getLogger().warn(
                "Warp destination server '{}' is not registered on this proxy - '{}' could not be connected.",
                targetServerName,
                sender.getUsername()
            );
            return;
        }

        RegisteredServer targetServer = optTargetServer.get();
        sender.createConnectionRequest(targetServer).connect()
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    sender.sendRichMessage(
                        "<red>Unable to connect to server '<server>' - server may be offline!",
                        Placeholder.unparsed("server", targetServerName)
                    );
                    WarpVelocity.getLogger().error(
                        "Server '{}' may be offline! '{}' was unable to connect.",
                        targetServerName,
                        sender.getUsername()
                    );
                    return;
                }

                if (!result.isSuccessful()) {
                    Component reason = result.getReasonComponent()
                        .orElse(Component.text("Unknown reason"));
                    sender.sendRichMessage(
                        "<red>Unable to connect to server '<server>' - <reason>",
                        Placeholder.unparsed("server", targetServerName),
                        Placeholder.component("reason", reason)
                    );
                    return;
                }

                sender.getCurrentServer().ifPresent(callback);
            });
    }
}