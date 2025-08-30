package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.SimpleLocation;
import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.mcmiddleearth.warps.velocity.config.Config;
import com.mcmiddleearth.warps.velocity.config.ConfigManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MyWarpDBConnector {

    private final String dbUser;
    private final String dbPassword;
    private final String dbName;
    private final String dbIp;
    private final int port;

    private final Path worldFile =  WarpVelocity.getInstance().getDataFolder().resolve("world.uuid");
    private final Map<String, String> worldUUID = new HashMap<>();

    private Connection dbConnection;
    private PreparedStatement getWarps;

    private boolean connected = false;

    public MyWarpDBConnector() {
        Config.Sql config = ConfigManager.getConfig().sql();
        dbUser = config.user();
        dbPassword = config.password();
        dbName = config.dbName();
        dbIp = config.ip();
        port = config.port();

        loadWorldUUIDs();
        connect();
    }

    public void disconnect() {
        connected = false;

        try {
            dbConnection.close();
        } catch (SQLException ex) {
            Logger.getLogger(MyWarpDBConnector.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void connect() {
        try {
            dbConnection = DriverManager.getConnection(
                "jdbc:mysql://"+dbIp+":"+port+"/"+dbName,
                dbUser, dbPassword);

            getWarps = dbConnection.prepareStatement("""
                SELECT
                    w.warp_id,
                    w.name,
                    w.type,
                    w.x, w.y, w.z, w.yaw, w.pitch,
                    w.creation_date,
                    w.visits,
                    w.welcome_message,
                    owner.uuid
                    world.uuid,
                    invited.uuid
                FROM warp w
                JOIN player AS owner ON warp.player_id = owner.player_id
                JOIN world ON w.world_id = world.world_id
                LEFT JOIN warp_player_map ON warp.warp_id = warp_player_map.warp_id
                LEFT JOIN player AS invited ON wpm.player_id = invited.player_id
                """);
            getWarps.setQueryTimeout(15);

        } catch (SQLException ex) {
            Logger.getLogger(MyWarpDBConnector.class.getName()).log(Level.SEVERE, null, ex);
            connected = false;
        }
    }

    public Collection<Warp> getWarps() {
        Map<String, Warp> warps = new HashMap<>();

        try (ResultSet rs = getWarps.executeQuery()) {
            while (rs.next()) {
                try {
                    String warpId = rs.getString("w.warp_id");

                    // Warps can have many members, resulting in many rows of the same warp
                    // Only create a new warp if we haven't already done so
                    Warp warp = warps.computeIfAbsent(warpId, id -> {
                        try {
                            return rowToWarp(rs);
                        } catch (SQLException e) {
                            WarpVelocity.getInstance().getLogger().error("Failed to create warp from row", e);
                            return null; // Skip broken row
                        }
                    });

                    if (warp != null) {
                        UUID memberUUID = rs.getObject("invited.uuid", UUID.class);
                        if (memberUUID != null) {
                            // FIXME: Remove player name from membership
                            warp.addPlayer(memberUUID);
                        }
                    }
                } catch (SQLException e) {
                    WarpVelocity.getInstance().getLogger().error("Error while reading warp result row", e);
                }
            }
        } catch (SQLException e) {
            WarpVelocity.getInstance().getLogger().error("Database error during getWarps()", e);
            connected = false;
            return Collections.emptyList();
        }

        return warps.values();
    }

    private Warp rowToWarp(ResultSet rs) throws SQLException {
        String world = worldUUID.get(rs.getString("world.uuid"));
        if (world == null) {
            world = "_unknown";
        }
        SimpleLocation loc = new SimpleLocation(
            world,
            rs.getDouble("w.x"),
            rs.getDouble("w.y"),
            rs.getDouble("w.z"),
            rs.getFloat("w.yaw"),
            rs.getFloat("w.pitch")
        );

        Warp.Type type = rs.getInt("w.type") == 1 ? Warp.Type.PUBLIC : Warp.Type.PRIVATE;

        Warp tempWarp = new Warp(
            rs.getObject("owner.uuid", UUID.class),
            rs.getString("w.name"),
            // FIXME: How to get the server??? Are they all named the same as the world?
            //  * What about plotworld?
            world,
            loc,
            type
        );
        tempWarp.setWelcomeMessage(rs.getString("w.welcome_message"));
        tempWarp.setVisits(rs.getInt("w.visits"));

        return tempWarp;
    }

    private void loadWorldUUIDs() {
        if (!Files.exists(worldFile)) {
            return;
        }

        try(Scanner scanner = new Scanner(worldFile)) {
            worldUUID.clear();
            while(scanner.hasNext()) {
                String[] line = scanner.nextLine().split(";");
                worldUUID.put(line[0], line[1]);
            }
        } catch (IOException ex) {
            Logger.getLogger(MyWarpDBConnector.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean isConnected() {
        return connected;
    }
}