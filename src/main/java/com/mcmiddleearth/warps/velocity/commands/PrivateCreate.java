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
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.text.MessageFormat;
import java.util.function.Predicate;

public class PrivateCreate {

    private static final SimpleCommandExceptionType WARP_EXISTS =
        new SimpleCommandExceptionType(() -> "A warp already exists with that name");

    private static final SimpleCommandExceptionType MANUAL_PREFIX =
        new SimpleCommandExceptionType(() -> "Private warps are automatically given the 'zzz' prefix, please just provide the warp name");

    private static final SimpleCommandExceptionType PRIVATE_WARP_LIMIT_REACHED =
        new SimpleCommandExceptionType(() -> "Unable to create another private warp - you have reached the maximum (" + ConfigManager.getConfig().privateWarpLimit() + ")");

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

        // Ensure the player hasn't hit their limit of private warps
        if (!sender.hasPermission(Permission.IGNORE_PRIVATE_WARPS_LIMIT.getNode())) {
            final int privateLimit = ConfigManager.getConfig().privateWarpLimit();
            final int senderPrivateWarpCount = WarpManager.getWarps(w -> w.isCreator(sender) && w.isOfType(Warp.Type.PRIVATE)).size();

            if (senderPrivateWarpCount >= privateLimit) {
                throw PRIVATE_WARP_LIMIT_REACHED.create();
            }
        }

        final String tempWarpName = context.getArgument("name", String.class);
        if (tempWarpName.startsWith("zzz")) {
            throw MANUAL_PREFIX.create();
        }
        final String privatisedWarpName = MessageFormat.format("zzz-{0}-{1}", sender.getUsername(), tempWarpName);

        CommandUtils.validateWarpName(privatisedWarpName);

        if (WarpManager.warpExists(privatisedWarpName)) {
            throw WARP_EXISTS.create();
        }

        // Send plugin message requesting player's location
        // the response will be used in the MessageListener
        sender.getCurrentServer().ifPresentOrElse(serverConnection -> {
            boolean status = serverConnection.sendPluginMessage(
                ChannelIdentifiers.PLAYER_LOCATION_CHANNEL_ID,
                RequestLocationMessage.serialise(LocationActionSubchannel.CREATE_PRIVATE, privatisedWarpName)
            );

            if (!status) {
                WarpVelocity.getLogger().error("Failed to send plugin message to paper backend {}", serverConnection.getServerInfo().getName());
            }
        }, () -> {
            sender.sendRichMessage("<red>You are not connected to a server");
        });

        return Command.SINGLE_SUCCESS;
    }
}
