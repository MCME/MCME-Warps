package com.mcmiddleearth.warps.bukkit.command;

import com.mcmiddleearth.command.builder.HelpfulLiteralBuilder;
import com.mcmiddleearth.command.handler.BukkitCommandHandler;
import com.mcmiddleearth.warps.Permission;

public class WarptestCommand extends BukkitCommandHandler {

    public WarptestCommand(String command) {
        super(command);
    }

    @Override
    protected HelpfulLiteralBuilder createCommandTree(HelpfulLiteralBuilder commandNodeBuilder) {
        commandNodeBuilder
            .requires(sender -> (sender.hasPermission(Permission.TEST.getNode())))
            .executes(context -> {
                context.getSource().sendMessage("Bukkit command handler works!");
                return 0;
            });
        return commandNodeBuilder;
    }
}
