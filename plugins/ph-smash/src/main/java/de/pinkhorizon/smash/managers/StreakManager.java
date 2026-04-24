package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class StreakManager {

    private final PHSmash plugin;

    public StreakManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the player's current win streak.
     */
    public int getStreak(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT current_streak FROM smash_streaks WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("current_streak");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("StreakManager.getStreak: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Returns the player's all-time best streak.
     */
    public int getBestStreak(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT best_streak FROM smash_streaks WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("best_streak");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("StreakManager.getBestStreak: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Increments the current streak by 1, updates best_streak if surpassed.
     * Returns the new streak value.
     */
    public int incrementStreak(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_streaks (uuid, current_streak, best_streak)
                 VALUES (?, 1, 1)
                 ON DUPLICATE KEY UPDATE
                   current_streak = current_streak + 1,
                   best_streak    = GREATEST(best_streak, current_streak + 1)
                 """)) {
            st.setString(1, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("StreakManager.incrementStreak: " + e.getMessage());
        }
        return getStreak(uuid);
    }

    /**
     * Resets the current streak to 0.
     */
    public void resetStreak(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO smash_streaks (uuid, current_streak, best_streak) VALUES (?, 0, 0) " +
                 "ON DUPLICATE KEY UPDATE current_streak = 0")) {
            st.setString(1, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("StreakManager.resetStreak: " + e.getMessage());
        }
    }

    /**
     * Returns a damage multiplier based on streak: +0.5% per streak level, no cap.
     */
    public double getStreakMultiplier(UUID uuid) {
        return 1.0 + 0.005 * getStreak(uuid);
    }

    /**
     * Returns a formatted display string for the current streak.
     */
    public String getStreakDisplay(UUID uuid) {
        int streak = getStreak(uuid);
        if (streak > 0) return "§6🔥" + streak;
        return "§8–";
    }
}
