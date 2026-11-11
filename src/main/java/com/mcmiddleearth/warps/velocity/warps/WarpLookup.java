package com.mcmiddleearth.warps.velocity.warps;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Decides which warp a name typed by a player reaches, and how a player addresses a given warp.
 * <p>
 * Private warps are kept per player, so one name can belong to several warps. A typed name is tried,
 * in this order, as:
 * <ol>
 *   <li>the player's own private warp;</li>
 *   <li>a public warp;</li>
 *   <li>somebody else's private warp that the player may see - written {@code owner:name}, or as the
 *       bare name when only one such warp has it.</li>
 * </ol>
 * Somebody else's warp comes last on purpose: a creator adds members without asking them, so a private
 * warp must never take over a name the player already uses for a public warp or a warp of their own.
 * <p>
 * Pure (no Velocity types): who may see or use a warp is passed in, so the rules can be unit tested.
 */
public final class WarpLookup {

    /** Separates the owner from the warp name. Forbidden in warp names, so it cannot be part of one. */
    public static final char OWNER_SEPARATOR = ':';

    private WarpLookup() {}

    public sealed interface Result permits Found, NotFound, NotAllowed, Ambiguous {}

    /** The name reaches exactly this warp, and the player may do with it what the command needs. */
    public record Found(Warp warp) implements Result {}

    /** No warp of that name that this player may know about. */
    public record NotFound() implements Result {}

    /** The warp exists and the player may see it, but not do what the command needs. */
    public record NotAllowed(Warp warp) implements Result {}

    /** Several players have a private warp of that name: the player has to say whose, as owner:name. */
    public record Ambiguous(List<Warp> candidates) implements Result {}

    /**
     * @param sender  the player asking, or null for the console
     * @param visible which of other players' private warps the sender may know about (a member, or
     *                staff); never asked about the sender's own warps or about public warps
     * @param allowed what the command needs of the warp (to use it, to modify it, ...)
     */
    public static Result find(
        WarpStore store,
        String input,
        @Nullable UUID sender,
        Predicate<Warp> visible,
        Predicate<Warp> allowed
    ) {
        Warp own = sender == null ? null : store.getPrivate(sender, input);
        if (own != null) {
            return allowed.test(own) ? new Found(own) : new NotAllowed(own);
        }

        Warp publicWarp = store.getPublic(input);
        if (publicWarp != null) {
            return allowed.test(publicWarp) ? new Found(publicWarp) : new NotAllowed(publicWarp);
        }

        // "owner:name" may also name the sender themselves; their own warps are always visible to them.
        List<Warp> seen = privateWarpsNamed(store, input).stream()
            .filter(warp -> warp.getCreatorId().equals(sender) || visible.test(warp))
            .toList();
        if (seen.isEmpty()) {
            return new NotFound();
        }
        List<Warp> usable = seen.stream()
            .filter(allowed)
            .sorted(Comparator.comparing(Warp::getCreatorName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        return switch (usable.size()) {
            case 0 -> new NotAllowed(seen.get(0));
            case 1 -> new Found(usable.get(0));
            default -> new Ambiguous(usable);
        };
    }

    /** The private warps {@code input} can mean: one player's for "owner:name", everybody's for a bare name. */
    private static List<Warp> privateWarpsNamed(WarpStore store, String input) {
        int separator = input.indexOf(OWNER_SEPARATOR);
        boolean hasOwner = separator > 0 && separator < input.length() - 1;
        if (!hasOwner) {
            return store.getPrivateByName(input);
        }
        String owner = input.substring(0, separator).strip();
        String name = input.substring(separator + 1);
        return store.getPrivateByName(name).stream()
            .filter(warp -> warp.getCreatorName().equalsIgnoreCase(owner))
            .toList();
    }

    /**
     * What {@code viewer} types to reach {@code warp}: its name, or {@code owner:name} for a private
     * warp of somebody else. {@link #find} leads every such address back to its warp.
     */
    public static String address(Warp warp, @Nullable UUID viewer) {
        boolean someoneElsesPrivateWarp = warp.isOfType(Warp.Type.PRIVATE) && !warp.getCreatorId().equals(viewer);
        return someoneElsesPrivateWarp
            ? warp.getCreatorName() + OWNER_SEPARATOR + warp.getName()
            : warp.getName();
    }
}
