package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.BaseWarp;
import com.mcmiddleearth.warps.core.Utils;
import net.kyori.adventure.text.Component;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynmapAPI implements MapAPI {

    private final MarkerAPI markerAPI;
    private final Map<String, MarkerSet> markerSets = new HashMap<>();
    private final MarkerIcon backupIcon;

    public DynmapAPI(DynmapCommonAPI dynmap, List<Layer> layers) {
        this.markerAPI = dynmap.getMarkerAPI();
        this.backupIcon = markerAPI.getMarkerIcon("greenflag");

        for (Layer layer : layers) {
            MarkerSet set = markerAPI.getMarkerSet(layer.name());
            if (set == null) {
                set = markerAPI.createMarkerSet(
                    /* Marker set ID */                 getSetId(layer.name()),
                    /* Marker set label */              layer.label(),
                    /* Set of permitted marker icons */ markerAPI.getMarkerIcons(),
                    /* Is marker set persistent */      false
                );
            }

            set.setMinZoom(layer.minZoom());
            set.setLayerPriority(layer.priority());

            set.setLabelShow(false);
            markerSets.put(set.getMarkerSetID(), set);
        }
    }

    @Override
    public void addMarker(BaseWarp warp) {
        String setId = getSetId(warp.getLayer());

        MarkerSet markerSet = markerSets.containsKey(setId)
            ? markerSets.get(setId)
            : markerSets.get(getSetId("default"));
        if (markerSet == null) {
            WarpPaper.getInstance().getComponentLogger().error(
                Component.text("Unable to add marker '" + warp.getName() + "' - failed to find a MarkerSet")
            );
            return;
        }

        MarkerIcon icon = getMarkerIcon(warp.getIcon().getValue());

        markerSet.createMarker(
            /* Marker ID */                  getMarkerId(warp.getName()),
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
    public void removeMarker(String warpName) {
        for (MarkerSet markerSet : markerSets.values()) {
            Marker marker = markerSet.findMarker(getMarkerId(warpName));
            if (marker != null) {
                marker.deleteMarker();
                return;
            }
        };
    }

    @Override
    public void clearMarkers() {
    }

    private String getMarkerId(String warpName) {
        return Utils.normaliseString(warpName) + "Id";
    }

    private String getSetId(String setName) {
        return "mcmewarps.markerset." + Utils.normaliseString(setName);
    }
}
