package de.pinkhorizon.core.database;

import de.pinkhorizon.core.integration.LuckPermsHook;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Zentrale Rang-Verwaltung für alle Pink Horizon Server.
 * Speichert Ränge in der gemeinsamen MySQL-Datenbank (players.rank).
 * Jeder neue Server erhält die Ränge automatisch ohne zusätzliche Konfiguration.
 */
public class RankRepository {

    private final DatabaseManager db;
    private final Logger log;

    public RankRepository(DatabaseManager db, Logger log) {
        this.db  = db;
        this.log = log;
    }

    /**
     * Lädt alle gespeicherten Ränge aus der DB (für den Startup-Cache).
     * @return Map UUID → rankId
     */
    public Map<UUID, String> loadAll() {
        Map<UUID, String> result = new HashMap<>();
        String sql = "SELECT uuid, `rank` FROM players WHERE `rank` IS NOT NULL";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                try {
                    result.put(UUID.fromString(rs.getString("uuid")), rs.getString("rank"));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            log.warning("[RankRepository] loadAll fehlgeschlagen: " + e.getMessage());
        }
        return result;
    }

    /**
     * Liest den Rang eines Spielers aus der DB.
     * @return rankId oder "spieler" falls nicht vorhanden
     */
    public String getRank(UUID uuid) {
        String sql = "SELECT `rank` FROM players WHERE uuid = ?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String rank = rs.getString("rank");
                    return rank != null ? rank : "spieler";
                }
            }
        } catch (SQLException e) {
            log.warning("[RankRepository] getRank fehlgeschlagen: " + e.getMessage());
        }
        return "spieler";
    }

    /**
     * Setzt den Rang eines Spielers in der DB und synchronisiert LuckPerms.
     * Legt den Spieler-Eintrag an falls noch nicht vorhanden.
     */
    public void setRank(UUID uuid, String playerName, String rankId) {
        String sql = db.getDbType().equals("mysql")
            ? "INSERT INTO players (uuid, name, `rank`) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `rank` = VALUES(`rank`), name = VALUES(name)"
            : "INSERT INTO players (uuid, name, rank) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET rank = excluded.rank, name = excluded.name";

        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, rankId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warning("[RankRepository] setRank fehlgeschlagen: " + e.getMessage());
            return;
        }

        LuckPermsHook.setGroup(uuid, rankId);
    }
}
