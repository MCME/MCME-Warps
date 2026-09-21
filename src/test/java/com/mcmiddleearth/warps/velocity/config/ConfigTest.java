package com.mcmiddleearth.warps.velocity.config;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deserialisation tests for the proxy config. No proxy, no database.
 * <p>
 * The {@code sql} block exists only for the dormant MyWarp importer, and
 * default-velocity-config.yml ships it commented out, so every shape of "not configured" has to
 * load cleanly. It did not: a {@code sql} node that existed but had no values threw Configurate's
 * "A value is required for this field" and took the whole proxy plugin down with it, which is what
 * forced the legacy MyWarp credentials back into the production config.
 * <p>
 * Note the distinction these tests pin: a fully absent block always worked. The failure needed the
 * node to be <em>present but valueless</em> - which is what you get by commenting out only the
 * value lines, or by uncommenting the default block without filling it in.
 */
class ConfigTest {

    private static Config load(String yaml) throws Exception {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .build();
        ConfigurationNode root = loader.load();
        return root.get(Config.class);
    }

    private static final String BASE = """
            warp-name-max-length: 64
            private-warp-limits:
              default-limit: 2
              configured: {}
            layer-keys:
              - major
            """;

    @Test
    void loadsWithTheSqlBlockFullyAbsent() throws Exception {
        Config config = assertDoesNotThrow(() -> load(BASE),
                "the shipped default comments this block out, so absence is the normal state");
        // Configurate still materialises the nested record with null components rather than
        // leaving the field null, so callers must ask isSqlConfigured rather than test for null.
        assertFalse(Config.isSqlConfigured(config.sql()),
                "an absent block is not a usable database configuration");
    }

    @Test
    void loadsWithAnEmptySqlNode() throws Exception {
        // `sql: {}` - or an uncommented header with nothing under it.
        Config config = assertDoesNotThrow(() -> load(BASE + "sql: {}\n"),
                "an empty sql node must not abort config loading");
        assertFalse(Config.isSqlConfigured(config.sql()),
                "an empty block is not a usable database configuration");
    }

    @Test
    void loadsWithSqlKeysPresentButValueless() throws Exception {
        // The exact shape that broke production: keys kept, values commented out or blanked.
        String yaml = BASE + """
                sql:
                  user:
                  password:
                  db-name:
                  ip:
                  port:
                """;
        Config config = assertDoesNotThrow(() -> load(yaml),
                "valueless sql keys must not throw 'A value is required for this field'");
        assertFalse(Config.isSqlConfigured(config.sql()),
                "a block with no values is not a usable database configuration");
    }

    @Test
    void loadsWithAPartiallyFilledSqlBlock() throws Exception {
        Config config = assertDoesNotThrow(() -> load(BASE + "sql:\n  user: u\n  password: p\n"),
                "a half-filled block must not abort startup either");
        assertFalse(Config.isSqlConfigured(config.sql()),
                "missing db-name/ip/port means the importer cannot connect");
    }

    @Test
    void stillReadsAFullyConfiguredSqlBlock() throws Exception {
        String yaml = BASE + """
                sql:
                  user: warpuser
                  password: secret
                  db-name: mywarp
                  ip: localhost
                  port: 3306
                """;
        Config config = load(yaml);
        assertTrue(Config.isSqlConfigured(config.sql()), "a complete block must still be usable");
        assertEquals("warpuser", config.sql().user());
        assertEquals("mywarp", config.sql().dbName());
        assertEquals(3306, config.sql().port());
    }

    @Test
    void theRestOfTheConfigIsUnaffected() throws Exception {
        Config config = load(BASE);
        assertEquals(64, config.warpNameMaxLength());
        assertEquals(2, config.privateWarpLimits().defaultLimit());
        assertEquals(java.util.List.of("major"), config.layerKeys());
    }
}
