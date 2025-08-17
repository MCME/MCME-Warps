package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.velocity.config.ConfigManager;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.velocitypowered.api.command.CommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class CommandUtils {

    private static final DynamicCommandExceptionType WARP_NOT_FOUND =
        new DynamicCommandExceptionType(warpName -> () -> "No warp found with name " + warpName);

    private static final DynamicCommandExceptionType NOT_ALLOWED =
        new DynamicCommandExceptionType((warpName) -> new LiteralMessage("You do not have access to warp " + warpName));

    private CommandUtils() { }

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

    private static final Dynamic2CommandExceptionType TOO_LONG =
        new Dynamic2CommandExceptionType(
            (warpName, maxLength) -> () ->
                "'%s' is too long (%d), warp names can't contain more than %d characters".formatted(
                    warpName,
                    ((String) warpName).length(),
                    (Integer) maxLength)
        );

    private static final DynamicCommandExceptionType INVALID_CHARS =
        new DynamicCommandExceptionType(invalidChars -> () -> "This warp name contains invalid characters: " + invalidChars);

    static final Set<Character> BLACKLIST = Set.of('/', '\\', '<', '>', ':', '"', '|', '*', '?', '!');

    public static void validateWarpName(String warpName) throws CommandSyntaxException {
        final int maxLength = ConfigManager.getConfig().getWarpNameMaxLength();
        if (warpName.length() > maxLength) {
            throw TOO_LONG.create(warpName, maxLength);
        }

        List<Character> badChars = getInvalidChars(warpName);
        if (!badChars.isEmpty()) {
            throw INVALID_CHARS.create(badChars);
        }
    }

    public static List<Character> getInvalidChars(String name) {
        List<Character> invalid = new ArrayList<>();
        for (char c : name.toCharArray()) {
            if (BLACKLIST.contains(c)) {
                invalid.add(c);
            }
        }
        return invalid;
    }
}
