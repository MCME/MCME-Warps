package com.mcmiddleearth.warps.velocity.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;

public class Create {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return BrigadierCommand.literalArgumentBuilder("create")
            .then(BrigadierCommand.requiredArgumentBuilder("warpName", StringArgumentType.word())
//                .suggests(Warp::suggest)
                    .executes(Create::execute)
            );
//        return Commands.literal("delete")
//            .then(Commands.argument("areaName", new AreaName())
//                .executes(Disable::execute)
//            );
    }

    private static int execute(CommandContext<CommandSource> ctx) {
//        final AreaNameResult result = ctx.getArgument("guidebookName", AreaNameResult.class);
//        final InfoArea infoArea = result.infoArea;
//
//        infoArea.statusOff();
//        PluginData.getMessageUtil().sendInfoMessage(ctx.getSource().getSender(), "Guidebook area " + result.areaName + " Disabled");
//
//        try {
//            // TODO: Painful to try-catch this everytime - why? just to provide the context of the subcommand
//            PluginData.saveArea(infoArea);
//        } catch (IOException ex) {
//            Logger.getLogger(GuidebookRename.class.getName()).log(Level.SEVERE, null, ex);
//        }
//
        return Command.SINGLE_SUCCESS;
    }
}
