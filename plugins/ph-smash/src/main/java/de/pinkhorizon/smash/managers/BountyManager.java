package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Manages the daily boss bounty system.
 * Each day a specific boss level is the bounty target; defeating it grants a one-time reward.
 *
 * DB table (create if not present):
 *   smash_bounty (uuid CHAR(36), bounty_date DATE,
 *                 PRIMARY KEY(uuid, bounty_date))
 */
public class BountyManager {

    private final PHSmash plugin;

    public BountyManager(PHSmash plugin) {
        this.plugin = plugin;
        createTable();
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    private void createTable() {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS smash_bounty (
                     uuid         CHAR(36) NOT NULL,
                     bounty_date  DATE     NOT NULL,
                     PRIMARY KEY (uuid, bounty_date)
                 )
                 """)) {
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("BountyManager.createTable: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Daily level
    // -------------------------------------------------------------------------

    /**
     * Returns today's deterministic bounty level in the range [10, 59].
     * Formula: (epochDay % 50) + 10 — cycles every 50 days.
     */
    public int getTodaysBountyLevel() {
        return (int) (LocalDate.now().toEpochDay() % 50) + 10;
    }

    // -------------------------------------------------------------------------
    // Claim logic
    // -------------------------------------------------------------------------

    /**
     * Returns true if the given player has already claimed today's bounty.
     */
    public boolean hasClaimed(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT 1 FROM smash_bounty WHERE uuid = ? AND bounty_date = ?")) {
            st.setString(1, uuid.toString());
            st.setDate(2, Date.valueOf(LocalDate.now()));
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("BountyManager.hasClaimed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Attempts to claim the bounty for the player.
     *
     * <ul>
     *   <li>Returns false if the defeated level does not match today's bounty level.</li>
     *   <li>Returns false if the player has already claimed today's bounty.</li>
     *   <li>On success: inserts the claim row, awards 1 000 coins and 3 × BOSS_CORE loot,
     *       then returns true. The caller (ArenaManager) is responsible for sending
     *       the player message.</li>
     * </ul>
     */
    public boolean tryClaimBounty(UUID uuid, int defeatedLevel) {
        if (defeatedLevel != getTodaysBountyLevel()) return false;
        if (hasClaimed(uuid)) return false;

        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "INSERT IGNORE INTO smash_bounty (uuid, bounty_date) VALUES (?, ?)")) {
            st.setString(1, uuid.toString());
            st.setDate(2, Date.valueOf(LocalDate.now()));
            int affected = st.executeUpdate();

            if (affected == 0) {
                // Race condition: another thread inserted first
                return false;
            }

            // Reward
            plugin.getCoinManager().addCoins(uuid, 1000);
            plugin.getLootManager().addLoot(uuid, LootItem.BOSS_CORE, 3);

            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("BountyManager.tryClaimBounty: " + e.getMessage());
        }
        return false;
    }
}
