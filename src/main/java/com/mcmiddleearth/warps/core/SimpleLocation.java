package com.mcmiddleearth.warps.core;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record SimpleLocation(String world, double x, double y, double z, float yaw, float pitch) {
}

//@ConfigSerializable
//public class SimpleLocation {
//    private String world;
//    private double x, y, z;
//    private float yaw, pitch;
//
//    public SimpleLocation() {}
//
//    public SimpleLocation(String world, double x, double y, double z, float yaw, float pitch) {
//        this.world = world;
//        this.x = x;
//        this.y = y;
//        this.z = z;
//        this.yaw = yaw;
//        this.pitch = pitch;
//    }
//
//    public String world() { return world; }
//    public double x() { return x; }
//    public double y() { return y; }
//    public double z() { return z; }
//    public float yaw() { return yaw; }
//    public float pitch() { return pitch; }
//
//    public void setWorld(String world) { this.world = world; }
//    public void setX(double x) { this.x = x; }
//    public void setY(double y) { this.y = y; }
//    public void setZ(double z) { this.z = z; }
//    public void setYaw(float yaw) { this.yaw = yaw; }
//    public void setPitch(float pitch) { this.pitch = pitch; }
//}
