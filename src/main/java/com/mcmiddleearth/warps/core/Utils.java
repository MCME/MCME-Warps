package com.mcmiddleearth.warps.core;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

public class Utils {

    public static String normaliseString(String s) {
        // Amon Dîn -> amon din
        // Helm's Deep -> helms deep
        return StringUtils.stripAccents(s)
            // Ignoring apostrophes
            .replace("'", "")
            // Collapse multiple spaces
            // Brigadier seems to do this itself, but just for safety
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }
}