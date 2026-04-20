package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;

import java.sql.*;
import java.util.*;

public class StatsManager {

    private final PHSurvival plugin;

    public StatsManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    // ── Tode ────────────────────────────────────────────────────────────

    public void addDeath(UUID uuid) {
        upsertIncrement(uuid, "deaths", 1);
    }

    public int getDeaths(UUID uuid) {
        return getInt(uuid, "deaths");
    }

    // ── Mob-Kills ────────────────────────────────────────────────────────

    public void addMobKill(UUID uuid) {
        upsertIncrement(uuid, "mob_kills", 1);
    }

    public int getMobKills(UUID uuid) {
        return getInt(uuid, "mob_kills");
    }

    // ── Spieler-Kills ────────────────────────────────────────────────────

    public void addPlayerKill(UUID uuid) {
        upsertIncrement(uuid, "player_kills", 1);
    }

    public int getPlayerKills(UUID uuid) {
        return getInt(uuid, "player_kills");
    }

    // ── Abgebaute Blöcke ─────────────────────────────────────────────────

    public void addBlocksBroken(UUID uuid, int count) {
        upsertIncrement(uuid, "blocks_broken", count);
    }

    public int getBlocksBroken(UUID uuid) {
        return getInt(uuid, "blocks_broken");
    }

    // ── Spielzeit (Minuten) ──────────────────────────────────────────────

    public void addPlaytime(UUID uuid, long minutes) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_stats (uuid, playtime) VALUES (?, ?)" +
                 " ON DUPLICATE KEY UPDATE playtime = playtime + VALUES(playtime)")) {
            st.setString(1, uuid.toString());
            st.setLong(2, minutes);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("StatsManager.addPlaytime: " + e.getMessage());
        }
    }

    public long getPlaytime(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT playtime FROM sv_stats WHERE uuid=?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("StatsManager.getPlaytime: " + e.getMessage());
        }
        return 0L;
    }

    // ── Top-Listen ───────────────────────────────────────────────────────

    public List<Map.Entry<UUID, Integer>> getTopMobKills(int limit) {
        List<Map.Entry<UUID, Integer>> result = new ArrayList<>();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT uuid, mob_kills FROM sv_stats ORDER BY mob_kills DESC LIMIT ?")) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next())
                    result.add(Map.entry(UUID.fromString(rs.getString("uuid")), rs.getInt("mob_kills")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("StatsManager.getTopMobKills: " + e.getMessage());
        }
        return result;
    }

    public List<Map.Entry<UUID, Long>> getTopPlaytime(int limit) {
        List<Map.Entry<UUID, Long>> result = new ArrayList<>();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT uuid, playtime FROM sv_stats ORDER BY playtime DESC LIMIT ?")) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next())
                    result.add(Map.entry(UUID.fromString(rs.getString("uuid")), rs.getLong("playtime")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("StatsManager.getTopPlaytime: " + e.getMessage());
        }
        return result;
    }

    // ── Intern ───────────────────────────────────────────────────────────

    private void upsertIncrement(UUID uuid, String column, int amount) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_stats (uuid, " + column + ") VALUES (?, ?)" +
                 " ON DUPLICATE KEY UPDATE " + column + " = " + column + " + VALUES(" + column + ")")) {
            st.setString(1, uuid.toString());
            st.setInt(2, amount);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("StatsManager.upsertIncrement(" + column + "): " + e.getMessage());
        }
    }

    private int getInt(UUID uuid, String column) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT " + column + " FROM sv_stats WHERE uuid=?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("StatsManager.getInt(" + column + "): " + e.getMessage());
        }
        return 0;
    }
}
