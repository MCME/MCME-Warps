package com.mcmiddleearth.warps.bungee.command;

import com.mcmiddleearth.command.builder.HelpfulLiteralBuilder;
import com.mcmiddleearth.command.handler.BungeeCommandHandler;
import com.mcmiddleearth.warps.Permission;

public class WarpCommand extends BungeeCommandHandler {

    public WarpCommand(String name) {
        super(name);
    }

    @Override
    protected HelpfulLiteralBuilder createCommandTree(HelpfulLiteralBuilder commandNodeBuilder) {
        commandNodeBuilder
                .then(HelpfulLiteralBuilder.literal("create")
                    .requires(sender -> (sender.hasPermission(Permission.CREATE.getNode())))
                    .executes(context -> {
                        context.getSource().sendMessage("Create warp!");
                        return 0;
                    }));
        return commandNodeBuilder;
    }
}
