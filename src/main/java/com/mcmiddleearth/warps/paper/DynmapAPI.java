package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.BaseWarp;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class DynmapAPI implements MapAPI {

    private static final String SET_ID = "mcmewarps.markerset";

    private final MarkerAPI markerAPI;
    private final MarkerSet markerSet;
    private final MarkerIcon backupIcon;

    public DynmapAPI(DynmapCommonAPI dynmap) {
        this.markerAPI = dynmap.getMarkerAPI();

        backupIcon = markerAPI.getMarkerIcon("greenflag");

        MarkerSet set = markerAPI.getMarkerSet(SET_ID);
        if (set == null) {
            set = markerAPI.createMarkerSet(
                /* Marker set ID */                 SET_ID,
                /* Marker set label */              "Warps",
                /* Set of permitted marker icons */ markerAPI.getMarkerIcons(),
                /* Is marker set persistent */      false
            );
        }

        this.markerSet = set;
    }

    @Override
    public void addMarker(BaseWarp warp) {
        MarkerIcon icon = getMarkerIcon(warp.getTag().getValue());

        Marker marker = markerSet.createMarker(
            /* Marker ID */                  warp.getName() + "Id",
            /* Marker label */               warp.getName(),
            /* Process label as HTML */      false,
            /* World to display marker in */ warp.getLocation().world(),
            /* X coordinate */               warp.getLocation().x(),
            /* Y coordinate */               warp.getLocation().y(),
            /* Z coordinate */               warp.getLocation().z(),
            /* Related MarkerIcon object */  icon,
            /* Marker is persistent */       false
        );
    }

    private MarkerIcon getMarkerIcon(String iconName) {
        MarkerIcon icon = markerAPI.getMarkerIcon(iconName);

        if (icon != null) return icon;

        return backupIcon;
    }

    @Override
    public void removeMarker(String id) {
    }

    @Override
    public void clearMarkers() {
    }
}
