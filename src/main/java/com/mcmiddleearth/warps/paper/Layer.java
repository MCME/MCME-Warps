package com.mcmiddleearth.warps.paper;

/**
 * @param name The key (or name) of the Map layer
 * @param label The displayed name of the layer
 * @param minZoom  The layer is hidden when zoom is below this value (0: zoomed out, 8: max zoomed in)
 * @param priority Lower priority appears higher in the list
 */
public record Layer(String name, String label, int minZoom, int priority) { }