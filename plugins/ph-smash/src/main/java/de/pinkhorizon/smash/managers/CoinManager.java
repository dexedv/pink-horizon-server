package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class CoinManager {

    private final PHSmash plugin;

    public CoinManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    public long getCoins(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT coins FROM smash_coins WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getLong("coins");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("CoinManager.getCoins: " + e.getMessage());
        }
        return 0;
    }

    /** Async – für Boss-Kill-Belohnung */
    public void addCoins(UUID uuid, long amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDb().getConnection();
                 PreparedStatement st = c.prepareStatement("""
                     INSERT INTO smash_coins (uuid, coins) VALUES (?, ?)
                     ON DUPLICATE KEY UPDATE coins = coins + VALUES(coins)
                     """)) {
                st.setString(1, uuid.toString());
                st.setLong(2, amount);
                st.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("CoinManager.addCoins: " + e.getMessage());
            }
        });
    }

    /**
     * Sync – für GUI-Klicks. Gibt false zurück wenn Coins nicht reichen.
     * Das UPDATE-Prädikat verhindert Race-Conditions.
     */
    public boolean spendCoins(UUID uuid, long amount) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE smash_coins SET coins = coins - ? WHERE uuid = ? AND coins >= ?")) {
            st.setLong(1, amount);
            st.setString(2, uuid.toString());
            st.setLong(3, amount);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("CoinManager.spendCoins: " + e.getMessage());
            return false;
        }
    }
}
