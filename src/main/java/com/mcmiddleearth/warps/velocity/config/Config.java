package com.mcmiddleearth.warps.velocity.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.List;
import java.util.Map;

@ConfigSerializable
public record Config(
    @Required int warpNameMaxLength,
    @Required Limits privateWarpLimits,
    Sql sql,
    List<String> layerKeys
) {
    @ConfigSerializable
    public record Limits(@Required Integer defaultLimit, @Required  Map<String, Integer> configured) {}

    /**
     * Connection details for the dormant MyWarp importer, used only when the warps directory is
     * missing and warps have to be pulled from the legacy database.
     * <p>
     * None of these components are {@code @Required}, deliberately. default-velocity-config.yml
     * ships this block commented out, so "not configured" is the normal state and must load
     * cleanly. Marking them required meant that a block which merely <em>existed</em> without
     * values - what you get by commenting out only the value lines, or by uncommenting the
     * default header without filling it in - failed deserialisation with "A value is required for
     * this field" and took the whole proxy plugin down. Absence is now expressed by
     * {@link #isSqlConfigured}, not by refusing to parse.
     */
    @ConfigSerializable
    public record Sql(String user, String password, String dbName, String ip, Integer port) {}

    /**
     * Whether the MyWarp importer has everything it needs to connect. False for an absent block,
     * an empty one, and a partially filled one alike.
     *
     * @param sql the parsed block, may be null
     * @return true only when every connection detail is present
     */
    public static boolean isSqlConfigured(Sql sql) {
        return sql != null
            && isSet(sql.user()) && isSet(sql.password())
            && isSet(sql.dbName()) && isSet(sql.ip())
            && sql.port() != null;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}