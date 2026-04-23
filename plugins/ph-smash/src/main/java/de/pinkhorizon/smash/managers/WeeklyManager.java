package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks per-player weekly boss kill stats and provides a leaderboard.
 *
 * DB table (create if not present):
 *   smash_weekly (uuid CHAR(36), week_start DATE, kills INT DEFAULT 0,
 *                 best_level INT DEFAULT 0, PRIMARY KEY(uuid, week_start))
 */
public class WeeklyManager {

    private final PHSmash plugin;

    public WeeklyManager(PHSmash plugin) {
        this.plugin = plugin;
        createTable();
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    private void createTable() {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS smash_weekly (
                     uuid        CHAR(36) NOT NULL,
                     week_start  DATE     NOT NULL,
                     kills       INT      NOT NULL DEFAULT 0,
                     best_level  INT      NOT NULL DEFAULT 0,
                     PRIMARY KEY (uuid, week_start)
                 )
                 """)) {
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("WeeklyManager.createTable: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Week helper
    // -------------------------------------------------------------------------

    /**
     * Returns the Monday of the current ISO week (the canonical week start key).
     */
    public LocalDate getWeekStart() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Upserts this week's row for the player:
     * increments kills by 1 and updates best_level if the given level is higher.
     */
    public void addKill(UUID uuid, int bossLevel) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_weekly (uuid, week_start, kills, best_level)
                 VALUES (?, ?, 1, ?)
                 ON DUPLICATE KEY UPDATE
                     kills      = kills + 1,
                     best_level = GREATEST(best_level, VALUES(best_level))
                 """)) {
            st.setString(1, uuid.toString());
            st.setDate(2, Date.valueOf(getWeekStart()));
            st.setInt(3, bossLevel);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("WeeklyManager.addKill: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the current week's kill count for the given player.
     */
    public int getPersonalKills(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT kills FROM smash_weekly WHERE uuid = ? AND week_start = ?")) {
            st.setString(1, uuid.toString());
            st.setDate(2, Date.valueOf(getWeekStart()));
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("kills");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("WeeklyManager.getPersonalKills: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Returns a ranked list of (playerName, kills) entries for this week,
     * ordered by kills descending.
     * Falls back to the UUID string if the player name cannot be resolved from smash_players.
     *
     * @param limit maximum number of entries to return
     */
    public List<AbstractMap.SimpleEntry<String, Integer>> getTopKills(int limit) {
        List<AbstractMap.SimpleEntry<String, Integer>> result = new ArrayList<>();
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 SELECT COALESCE(p.name, w.uuid) AS name, w.kills
                 FROM smash_weekly w
                 LEFT JOIN smash_players p ON p.uuid = w.uuid
                 WHERE w.week_start = ?
                 ORDER BY w.kills DESC
                 LIMIT ?
                 """)) {
            st.setDate(1, Date.valueOf(getWeekStart()));
            st.setInt(2, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.add(new AbstractMap.SimpleEntry<>(
                            rs.getString("name"),
                            rs.getInt("kills")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("WeeklyManager.getTopKills: " + e.getMessage());
        }
        return result;
    }
}
