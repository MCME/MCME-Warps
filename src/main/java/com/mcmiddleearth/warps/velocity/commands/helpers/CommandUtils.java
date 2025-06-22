package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.velocitypowered.api.command.CommandSource;

public class CommandUtils {

    private static final DynamicCommandExceptionType WARP_NOT_FOUND =
        new DynamicCommandExceptionType(name -> new LiteralMessage("No warp found with name: " + name));

    public static ArgResult<Warp> getWarp(CommandContext<CommandSource> context, String argumentName) throws CommandSyntaxException {
        final String warpName = context.getArgument(argumentName, String.class);

        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) {
            throw WARP_NOT_FOUND.create(warpName);
        }

        return new ArgResult<>(warpName, warp);
    }
}
