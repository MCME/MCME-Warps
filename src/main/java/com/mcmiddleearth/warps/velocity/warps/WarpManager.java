package com.mcmiddleearth.warps.velocity.warps;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WarpManager {
    private static final HashMap<String, Warp> warps = new HashMap<>();

//    public WarpManager(File dataFolder) {
    // TODO: Static? Singleton?
    public WarpManager() {
//        this.file = new File(dataFolder, "warps.yml");
//        this.config = YamlConfiguration.loadConfiguration(file);
//        loadWarps();
    }

    public static @Nullable Warp getWarp(String warpName) {
        if (warps.containsKey(warpName)) {
            return warps.get(warpName);
        }

        return null;
    }

    public static void saveWarp(Warp warp) {
//        ConfigurationSection section = config.createSection(warp.getName());
//        section.set("server", warp.getServer());
//        section.set("world", warp.getWorld());
//        section.set("x", warp.getX());
//        section.set("y", warp.getY());
//        section.set("z", warp.getZ());
//        section.set("yaw", warp.getYaw());
//        section.set("pitch", warp.getPitch());
//
//        try {
//            config.save(file);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        // Q: Good idea to store warp names lowercase?
//        warps.put(warp.getName().toLowerCase(), warp);
        // FIXME: Prevent dupes
        warps.put(warp.getName(), warp);
    }

//    public boolean warpExists(String name) {
//        return warps.containsKey(name.toLowerCase());
//    }

//    public static Collection<Warp> getAllWarps() {
//        return warps.values();
//    }

    public static Set<String> getAllWarpNames() {
//        return new ArrayList<String>(warps.keySet());
        return warps.keySet();
    }
}
