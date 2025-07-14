package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.velocitypowered.api.command.CommandSource;

import java.util.function.Predicate;

public class CommandUtils {

    private static final DynamicCommandExceptionType WARP_NOT_FOUND =
        new DynamicCommandExceptionType(warpName -> new LiteralMessage("No warp found with name: " + warpName));

    private static final DynamicCommandExceptionType NOT_ALLOWED =
        new DynamicCommandExceptionType((warpName) -> new LiteralMessage("You do not have access to warp " + warpName));

    public static ArgResult<Warp> getWarp(CommandContext<CommandSource> context, String argumentName) throws CommandSyntaxException {
        return getWarp(context, argumentName, _ -> true);
    }

    public static ArgResult<Warp> getWarp(
        CommandContext<CommandSource> context,
        String argumentName,
        Predicate<Warp> accessPredicate
    ) throws CommandSyntaxException {
        final String warpName = context.getArgument(argumentName, String.class);

        Warp warp = WarpManager.getWarp(warpName);
        if (warp == null) {
            throw WARP_NOT_FOUND.create(warpName);
        }

        if (!accessPredicate.test(warp)) {
            throw NOT_ALLOWED.create(warp.getName());
        }

        return new ArgResult<>(warpName, warp);
    }
}
