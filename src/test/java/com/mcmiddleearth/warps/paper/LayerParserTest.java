package com.mcmiddleearth.warps.paper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerParserTest {

    private static YamlConfiguration config(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    private static Layer named(List<Layer> layers, String name) {
        return layers.stream()
            .filter(l -> l.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no layer named '" + name + "' in " + layers));
    }

    @Test
    void readsMinZoomOfACustomLayerFromTheMinZoomKeyUsedInConfigYml() {
        List<Layer> layers = LayerParser.parse(config("""
            layers:
              default:
                label: "Other warps"
                min-zoom: 1
                priority: 8
              custom:
                major:
                  label: "Major warps"
                  min-zoom: 5
                  priority: 9
            """));

        assertEquals(5, named(layers, "major").minZoom(),
            "custom layers must read 'min-zoom', the key the shipped config.yml actually writes");
    }

    /**
     * Characterisation test - this already passed before any fix. Bukkit treats a key with no
     * body as absent, so it never reaches getKeys(false). Pinning it so that stays true.
     */
    @Test
    void treatsACustomLayerKeyWithNoBodyAsAbsent() {
        List<Layer> layers = assertDoesNotThrow(() -> LayerParser.parse(config("""
            layers:
              default:
                label: "Other warps"
                min-zoom: 1
              custom:
                broken:
                major:
                  label: "Major warps"
                  min-zoom: 5
            """)));

        assertEquals(5, named(layers, "major").minZoom(), "the valid layer must survive a malformed sibling");
        assertTrue(layers.stream().noneMatch(l -> l.name().equals("broken")), "the empty key must not become a layer");
    }

    @Test
    void skipsACustomLayerWhoseValueIsNotASectionRatherThanThrowing() {
        // 'broken: "oops"' IS returned by getKeys(false), but getConfigurationSection() gives
        // null for it - dereferencing that kills loadLayers(), and with it the plugin's whole
        // dynmap setup on that backend. One config typo must not cost every layer and marker.
        List<Layer> layers = assertDoesNotThrow(() -> LayerParser.parse(config("""
            layers:
              default:
                label: "Other warps"
                min-zoom: 1
              custom:
                broken: "oops"
                major:
                  label: "Major warps"
                  min-zoom: 5
            """)));

        assertEquals(5, named(layers, "major").minZoom(), "the valid layer must survive a malformed sibling");
        assertTrue(layers.stream().noneMatch(l -> l.name().equals("broken")), "the malformed layer must be skipped");
    }
}
