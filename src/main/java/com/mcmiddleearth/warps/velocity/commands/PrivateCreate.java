package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.LocationActionSubchannel;
import com.mcmiddleearth.warps.core.messageprotocols.RequestLocationMessage;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
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

public class PrivateCreate {

    private static final SimpleCommandExceptionType WARP_EXISTS =
        new SimpleCommandExceptionType(() -> "A warp already exists with that name");

    private static final SimpleCommandExceptionType MANUAL_PREFIX =
        new SimpleCommandExceptionType(() -> "Private warps are automatically given the 'zzz' prefix, please just provide the warp name");

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("pcreate")
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

        // TODO: Ensure warpName is valid (doesn't start with '-' etc.)
        // FIXME: Now using greedy, need to prevent bad file names e.g. invalid characters /?!
        final String tempWarpName = context.getArgument("name", String.class);

        if (tempWarpName.startsWith("zzz")) {
            throw MANUAL_PREFIX.create();
        }

        final String privatisedWarpName = MessageFormat.format("zzz-{0}-{1}", sender.getUsername(), tempWarpName);

        if (WarpManager.warpExists(privatisedWarpName)) {
            throw WARP_EXISTS.create();
        }

        // Send plugin message requesting player's Location
        sender.getCurrentServer().ifPresentOrElse(serverConnection -> {
            sender.sendMessage(Component.text("Creating warp '" + privatisedWarpName + '"'));

            boolean status = serverConnection.sendPluginMessage(
                ChannelIdentifiers.PLAYER_LOCATION_CHANNEL_ID,
                RequestLocationMessage.serialise(LocationActionSubchannel.CREATE_PRIVATE, privatisedWarpName)
            );

            // TODO
            // if (!status) { }
        }, () -> {
            // TODO: Report no connections
        });

        return Command.SINGLE_SUCCESS;
    }
}
