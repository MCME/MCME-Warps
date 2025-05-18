package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.core.CreateSubchannels;
import com.mcmiddleearth.warps.core.messageprotocols.RequestLocationMessage;
import com.mcmiddleearth.warps.velocity.ChannelIdentifiers;
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

public class Create {

    private static final SimpleCommandExceptionType WARP_EXISTS =
        new SimpleCommandExceptionType(() -> "A warp already exists with that name");

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("create")
            .then(BrigadierCommand.requiredArgumentBuilder("warpName", StringArgumentType.word())
                    .executes(Create::execute)
            );
    }

    // Q: Forward create commands to the backend instead?
    // - Removes the need for RequestLocationMessages
    private static int execute(CommandContext<CommandSource> context) throws CommandSyntaxException {

        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            // Q: Easy way to add this to the entire /warp tree? Using permissions?
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        final String warpName = context.getArgument("warpName", String.class);
        // TODO: Make a boolean helper in WarpManager
        Warp warp = WarpManager.getWarp(warpName);
        if (warp != null) {
            throw WARP_EXISTS.create();
        }

        // TODO: Ensure warpName is valid (doesn't start with '-')

        // Send plugin message requesting player's Location
        player.getCurrentServer().ifPresentOrElse(serverConnection -> {
            boolean status = serverConnection.sendPluginMessage(
                ChannelIdentifiers.CREATE_CHANNEL_ID,
                RequestLocationMessage.serialise(CreateSubchannels.CREATE_PUBLIC, warpName)
            );

            // TODO
            // if (!status) { }
        }, () -> {
            // TODO: Report no connections
        });

        return Command.SINGLE_SUCCESS;
    }
}
