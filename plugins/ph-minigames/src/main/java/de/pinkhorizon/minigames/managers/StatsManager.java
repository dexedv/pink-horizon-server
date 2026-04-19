package de.pinkhorizon.minigames.managers;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.minigames.PHMinigames;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class StatsManager {

    private final PHMinigames plugin;

    public StatsManager(PHMinigames plugin) {
        this.plugin = plugin;
        createTable();
    }

    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS minigame_stats (
                    uuid VARCHAR(36) PRIMARY KEY,
                    bedwars_wins INT DEFAULT 0,
                    bedwars_kills INT DEFAULT 0,
                    skywars_wins INT DEFAULT 0,
                    skywars_kills INT DEFAULT 0
                );
                """;
        try (PreparedStatement stmt = PHCore.getInstance().getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("Stats-Tabelle konnte nicht erstellt werden: " + e.getMessage());
        }
    }

    public int getStat(UUID uuid, String column) {
        String sql = "SELECT " + column + " FROM minigame_stats WHERE uuid = ?";
        try (PreparedStatement stmt = PHCore.getInstance().getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(column);
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Lesen von Stats: " + e.getMessage());
        }
        return 0;
    }

    public void incrementStat(UUID uuid, String column) {
        String sql = "INSERT INTO minigame_stats (uuid, " + column + ") VALUES (?, 1) "
                + "ON CONFLICT(uuid) DO UPDATE SET " + column + " = " + column + " + 1;";
        try (PreparedStatement stmt = PHCore.getInstance().getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Speichern von Stats: " + e.getMessage());
        }
    }
}
