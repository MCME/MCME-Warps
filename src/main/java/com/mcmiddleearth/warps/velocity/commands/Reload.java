package com.mcmiddleearth.warps.velocity.commands;

import com.mcmiddleearth.warps.velocity.Permission;
import com.mcmiddleearth.warps.velocity.config.ConfigManager;
import com.mcmiddleearth.warps.velocity.warps.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;

public class Reload {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("reload")
            .requires(sender -> sender.hasPermission(Permission.RELOAD.getNode()))
            .executes(Reload::execute);
    }

    private static int execute(CommandContext<CommandSource> context) {
        // Visits are only saved on shutdown, so this is needed
        WarpManager.saveWarpVisits();

        // Reload the config & warps
        ConfigManager.loadConfig();
        WarpManager.loadAllWarps();

        return Command.SINGLE_SUCCESS;
    }
}
