package com.mcmiddleearth.warps.velocity.commands.helpers;

import com.mcmiddleearth.warps.velocity.warps.Warp;
import com.velocitypowered.api.proxy.Player;

import java.util.function.Predicate;

public class WarpPredicates {
    public static Predicate<Warp> usableBy(Player player) {
        return warp -> warp.isUsable(player);
    }

    public static Predicate<Warp> modifiableBy(Player player) {
        return warp -> warp.isModifiable(player);
    }
}
