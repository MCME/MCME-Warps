package com.mcmiddleearth.warps.paper;

import com.mcmiddleearth.warps.core.BaseWarp;

public interface MapAPI {
    void addMarker(BaseWarp warp);
    void removeMarker(String warpName);
    void clearMarkers();
}