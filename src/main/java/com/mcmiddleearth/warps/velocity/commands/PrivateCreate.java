package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.messageprotocols.LocationActionSubchannel;
import com.mcmiddleearth.warps.core.messageprotocols.RequestLocationMessage;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
import com.mcmiddleearth.warps.velocity.Permission;
import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.mcmiddleearth.warps.velocity.commands.helpers.CommandUtils;
import com.mcmiddleearth.warps.velocity.config.ConfigManager;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.function.Predicate;

public class PrivateCreate {

    private static final SimpleCommandExceptionType WARP_EXISTS =
        new SimpleCommandExceptionType(() -> "A warp already exists with that name");

    private static final Dynamic2CommandExceptionType PRIVATE_WARP_LIMIT_REACHED =
        new Dynamic2CommandExceptionType((limitName, limit) -> () -> "Unable to create another private warp - you have reached the maximum (%s: %s). ".formatted(limitName, limit));

    public static LiteralArgumentBuilder<CommandSource> register(Predicate<CommandSource> requirement) {
        return BrigadierCommand.literalArgumentBuilder("pcreate")
            .requires(requirement)
            .then(BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.greedyString())
                .executes(PrivateCreate::execute)
            );
    }

    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {

        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        // Ensure the player hasn't hit their private warp limit
        if (!sender.hasPermission(Permission.IGNORE_PRIVATE_WARPS_LIMIT.getNode())) {
            ConfigManager.WarpLimit warpLimit = ConfigManager.resolvePrivateWarpLimit(sender);
            final int senderPrivateWarpCount = WarpManager.getWarps(w -> w.isCreator(sender) && w.isOfType(Warp.Type.PRIVATE)).size();

            if (senderPrivateWarpCount >= warpLimit.limit()) {
                throw PRIVATE_WARP_LIMIT_REACHED.create(warpLimit.name(), warpLimit.limit());
            }
        }

        // Private warps are keyed per-creator now (root-fix #1): the name is stored as-is, with no
        // zzz-<player>- prefix. Two players may each have a private "home".
        final String warpName = context.getArgument("name", String.class);
        CommandUtils.validateWarpName(warpName);

        if (WarpManager.privateWarpExists(sender.getUniqueId(), warpName)) {
            throw WARP_EXISTS.create();
        }

        // Send plugin message requesting player's location
        // the response will be used in the MessageListener
        sender.getCurrentServer().ifPresentOrElse(serverConnection -> {
            boolean status = serverConnection.sendPluginMessage(
                ChannelIdentifiers.PLAYER_LOCATION_CHANNEL_ID,
                RequestLocationMessage.serialise(LocationActionSubchannel.CREATE_PRIVATE, warpName)
            );

            if (!status) {
                WarpVelocity.getLogger().error("Failed to send plugin message to paper backend {}", serverConnection.getServerInfo().getName());
            }
        }, () -> sender.sendRichMessage("<red>You are not connected to a server"));

        return Command.SINGLE_SUCCESS;
    }
}
