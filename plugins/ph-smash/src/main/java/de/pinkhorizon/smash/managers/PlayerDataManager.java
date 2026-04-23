package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerDataManager {

    private final PHSmash plugin;

    public PlayerDataManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    private Connection con() throws SQLException {
        return plugin.getDb().getConnection();
    }

    public void ensurePlayer(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT IGNORE INTO smash_players (uuid) VALUES (?)")) {
            st.setString(1, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDataManager.ensurePlayer: " + e.getMessage());
        }
    }

    public int getKills(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT kills FROM smash_players WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("kills");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDataManager.getKills: " + e.getMessage());
        }
        return 0;
    }

    public long getTotalDamage(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT total_damage FROM smash_players WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getLong("total_damage");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDataManager.getTotalDamage: " + e.getMessage());
        }
        return 0;
    }

    /** Persönliches Boss-Level des Spielers (wo er beim nächsten Join weitermacht) */
    public int getPersonalBossLevel(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT personal_level FROM smash_players WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return Math.max(1, rs.getInt("personal_level"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDataManager.getPersonalBossLevel: " + e.getMessage());
        }
        return 1;
    }

    public void setPersonalBossLevel(UUID uuid, int level) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_players (uuid, personal_level, last_seen)
                 VALUES (?, ?, NOW())
                 ON DUPLICATE KEY UPDATE
                   personal_level = VALUES(personal_level),
                   last_seen      = NOW()
                 """)) {
            st.setString(1, uuid.toString());
            st.setInt(2, Math.min(999, Math.max(1, level)));
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDataManager.setPersonalBossLevel: " + e.getMessage());
        }
    }

    public void addKillAndDamage(UUID uuid, long damage, int bossLevel) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_players (uuid, kills, total_damage, best_level, last_seen)
                 VALUES (?, 1, ?, ?, NOW())
                 ON DUPLICATE KEY UPDATE
                   kills        = kills + 1,
                   total_damage = total_damage + VALUES(total_damage),
                   best_level   = GREATEST(best_level, VALUES(best_level)),
                   last_seen    = NOW()
                 """)) {
            st.setString(1, uuid.toString());
            st.setLong(2, damage);
            st.setInt(3, bossLevel);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDataManager.addKillAndDamage: " + e.getMessage());
        }
    }

    public List<AbstractMap.SimpleEntry<String, Integer>> getTopKills(int limit) {
        List<AbstractMap.SimpleEntry<String, Integer>> result = new ArrayList<>();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT uuid, kills FROM smash_players ORDER BY kills DESC LIMIT ?")) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.add(new AbstractMap.SimpleEntry<>(
                        rs.getString("uuid"), rs.getInt("kills")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDataManager.getTopKills: " + e.getMessage());
        }
        return result;
    }

    // Behalten für Dashboard-Kompatibilität
    public int getGlobalBossLevel() {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT boss_level FROM smash_state WHERE id = 1")) {
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("boss_level");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDataManager.getGlobalBossLevel: " + e.getMessage());
        }
        return 1;
    }

    public void setGlobalBossLevel(int level) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE smash_state SET boss_level = ? WHERE id = 1")) {
            st.setInt(1, Math.min(999, Math.max(1, level)));
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("PlayerDataManager.setGlobalBossLevel: " + e.getMessage());
        }
    }
}
