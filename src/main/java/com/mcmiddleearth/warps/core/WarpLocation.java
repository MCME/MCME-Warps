package com.mcmiddleearth.warps.core;

// Q: Rename to WorldLocation, RawLocation/BareLocation?
public record WarpLocation(String world, double x, double y, double z, float yaw, float pitch) {
}
