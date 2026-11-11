package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.velocity.config.ConfigManager;
import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpLookup;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommandUtils {

    private static final DynamicCommandExceptionType WARP_NOT_FOUND =
        new DynamicCommandExceptionType(warpName -> () -> "No warp found with name " + warpName);

    private static final DynamicCommandExceptionType NOT_ALLOWED =
        new DynamicCommandExceptionType((warpName) -> new LiteralMessage("You do not have access to warp " + warpName));

    private static final DynamicCommandExceptionType AMBIGUOUS =
        new DynamicCommandExceptionType(message -> () -> (String) message);

    private CommandUtils() { }

    public static ArgResult<Warp> getWarp(CommandContext<CommandSource> context, String argumentName) throws CommandSyntaxException {
        return getWarp(context, argumentName, warp -> true);
    }

    public static ArgResult<Warp> getWarp(
        CommandContext<CommandSource> context,
        String argumentName,
        Predicate<Warp> accessPredicate
    ) throws CommandSyntaxException {
        final String warpName = context.getArgument(argumentName, String.class);
        Player sender = context.getSource() instanceof Player p ? p : null;

        return switch (findWarp(sender, warpName, accessPredicate)) {
            case WarpLookup.Found found -> new ArgResult<>(warpName, found.warp());
            case WarpLookup.NotAllowed refused -> throw NOT_ALLOWED.create(refused.warp().getName());
            case WarpLookup.Ambiguous ambiguous -> throw AMBIGUOUS.create(whoseDoYouMean(ambiguous.candidates()));
            case WarpLookup.NotFound notFound -> throw WARP_NOT_FOUND.create(warpName);
        };
    }

    /**
     * What {@code input} typed by {@code sender} reaches, for a command that needs {@code allowed} of
     * the warp. A private warp of somebody else only counts when the sender may use or modify it (a
     * member, or staff with an override permission); to everyone else it does not exist.
     *
     * @param sender the player, or null for the console, which may see every warp
     */
    public static WarpLookup.Result findWarp(@Nullable Player sender, String input, Predicate<Warp> allowed) {
        if (sender == null) {
            return WarpManager.findWarp(input, null, warp -> true, allowed);
        }
        return WarpManager.findWarp(
            input,
            sender.getUniqueId(),
            warp -> warp.isUsable(sender) || warp.isModifiable(sender),
            allowed
        );
    }

    /** What {@code viewer} types to reach {@code warp}: its name, or owner:name for somebody else's private warp. */
    public static String addressOf(Warp warp, @Nullable Player viewer) {
        return WarpLookup.address(warp, viewer == null ? null : viewer.getUniqueId());
    }

    private static final int MAX_OWNERS_NAMED = 8;

    private static String whoseDoYouMean(List<Warp> sameNamedWarps) {
        String name = sameNamedWarps.get(0).getName();
        String owners = sameNamedWarps.stream()
            .limit(MAX_OWNERS_NAMED)
            .map(Warp::getCreatorName)
            .collect(Collectors.joining(", "));
        if (sameNamedWarps.size() > MAX_OWNERS_NAMED) owners += ", ...";

        return "%d players have a private warp called '%s' (%s). Put the owner's name in front, for example %s"
            .formatted(sameNamedWarps.size(), name, owners, WarpLookup.address(sameNamedWarps.get(0), null));
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
        final int maxLength = ConfigManager.getConfig().warpNameMaxLength();
        if (warpName.length() > maxLength) {
            throw TOO_LONG.create(warpName, maxLength);
        }

        List<Character> badChars = getInvalidChars(warpName);
        if (!badChars.isEmpty()) {
            // Join plainly ("/ *") instead of letting List.toString show "[/, *]" to the player.
            StringBuilder invalid = new StringBuilder();
            for (char c : badChars) {
                if (invalid.length() > 0) invalid.append(' ');
                invalid.append(c);
            }
            throw INVALID_CHARS.create(invalid.toString());
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
