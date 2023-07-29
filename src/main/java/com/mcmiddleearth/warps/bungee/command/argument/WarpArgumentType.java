package com.mcmiddleearth.warps.bungee.command.argument;

import com.mcmiddleearth.command.argument.AbstractSuggestionListArgumentType;
import com.mcmiddleearth.command.sender.McmeCommandSender;

import java.util.Collection;

public class WarpArgumentType extends AbstractSuggestionListArgumentType {

    McmeCommandSender sender;

    public WarpArgumentType(McmeCommandSender sender) {
        setTooltip("any warp name");
        this.sender = sender;
    }

    @Override
    protected Collection<String> getSuggestions() {
        //TODO: make sure only warps visible to the command sender are listed.
        return null;
    }

}
