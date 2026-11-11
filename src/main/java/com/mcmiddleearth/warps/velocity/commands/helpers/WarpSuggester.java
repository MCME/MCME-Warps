package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.VelocityBrigadierMessage;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Turns the warps picked by {@link WarpSuggestions} into Brigadier suggestions with a tooltip. Which
 * warps are offered, and under what text, is decided there.
 */
public class WarpSuggester {
    private static final NumberFormat compact = NumberFormat.getCompactNumberInstance();

    private static String formatRelative(String createdAt) {
        Duration d = Duration.between(Instant.parse(createdAt), Instant.now());

        long years = d.toDays() / 365;
        if (years > 0) return years + "y ago";

        long months = d.toDays() / 30;
        if (months > 0) return months + "mo ago";

        long days = d.toDays();
        if (days > 0) return days + "d ago";

        long hours = d.toHours();
        if (hours > 0) return hours + "h ago";

        long minutes = d.toMinutes();
        if (minutes > 0) return minutes + "m ago";

        return "just now";
    }

    public static Component buildWarpTooltip(Warp warp) {
        Component divider = Component.text(" | ", NamedTextColor.BLUE);
        TextComponent.Builder tooltip = Component.text().content("");

        int visits = warp.getVisits();
        tooltip.append(Component.text(compact.format(visits) + " visits"));
        tooltip.append(divider);

        String warpWorld = warp.getLocation().world();
        tooltip.append(Component.text(warpWorld));

        // Only show the server if it is different to the world
        String warpServer = warp.getServer();
        boolean showServer = !warpServer.equalsIgnoreCase(warpWorld);
        if (showServer) tooltip.append(Component.text(" (" + warpServer + ")"));
        tooltip.append(divider);

        tooltip.append(Component.text(warp.getCreatorName()));

        String relative = formatRelative(warp.getCreatedAt());
        tooltip.append(Component.text(" " + relative));

        if (warp.isOfType(Warp.Type.PRIVATE)) {
            tooltip.append(Component.text(" 🔒", NamedTextColor.RED));
        }

        return tooltip.build();
    }

    /** Offers the warps {@code filter} accepts for what {@code viewer} has typed so far. */
    public static void suggest(SuggestionsBuilder builder, @Nullable Player viewer, Predicate<Warp> filter) {
        suggest(builder, viewer, filter, false);
    }

    /**
     * @param shouldAddQuotes for arguments that are followed by another argument: those only take a
     *                        name with spaces or an {@code owner:name} address inside quotes
     */
    public static void suggest(SuggestionsBuilder builder, @Nullable Player viewer, Predicate<Warp> filter, boolean shouldAddQuotes) {
        UUID viewerId = viewer == null ? null : viewer.getUniqueId();
        List<WarpSuggestions.Entry> entries =
            WarpSuggestions.select(WarpManager.listWarps(w -> true), viewerId, filter, builder.getRemainingLowerCase());

        for (WarpSuggestions.Entry entry : entries) {
            String text = shouldAddQuotes ? "\"" + entry.text() + "\"" : entry.text();
            Message tooltip = VelocityBrigadierMessage.tooltip(buildWarpTooltip(entry.warp()));
            builder.suggest(text, tooltip);
        }
    }
}
