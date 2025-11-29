package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class WarpHelper {
    public static int execute(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(Component.text("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }

        Component prefix = Component.text("")
            .append(Component.text("Warp » ", NamedTextColor.DARK_AQUA, TextDecoration.BOLD))
            .append(Component.text("Please provide a location e.g. ", NamedTextColor.RED))
            .append(Component.text("/warp Rivendell", NamedTextColor.GOLD));

        sender.sendMessage(prefix);
        return 1;
    }
}