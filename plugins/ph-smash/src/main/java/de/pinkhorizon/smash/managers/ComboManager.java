package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player kill combos with in-memory cache and DB persistence.
 * A combo resets when the player dies, leaves, or explicitly resets.
 * DB table: smash_combo (uuid CHAR(36) PRIMARY KEY, combo INT NOT NULL DEFAULT 0)
 */
public class ComboManager {

    private final PHSmash           plugin;
    private final Map<UUID, Integer> cache = new ConcurrentHashMap<>();

    public ComboManager(PHSmash plugin) {
        this.plugin = plugin;
        createTable();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private void createTable() {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS smash_combo (
                     uuid  CHAR(36) NOT NULL,
                     combo INT      NOT NULL DEFAULT 0,
                     PRIMARY KEY (uuid)
                 )
                 """)) {
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("ComboManager.createTable: " + e.getMessage());
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Increments the combo counter for the given player by 1.
     *
     * @return the new combo count after incrementing
     */
    public int increment(UUID uuid) {
        int newCount = cache.getOrDefault(uuid, loadFromDb(uuid)) + 1;
        cache.put(uuid, newCount);
        saveAsync(uuid, newCount);
        return newCount;
    }

    /**
     * Resets the combo counter for the given player to 0.
     */
    public void reset(UUID uuid) {
        cache.put(uuid, 0);
        saveAsync(uuid, 0);
    }

    /**
     * Returns the current combo count for the player (cache-first, then DB).
     */
    public int getCombo(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDb);
    }

    /**
     * Returns the damage multiplier for the current combo.
     * Formula: 1.0 + 0.02 * min(combo, 25) — capped at 1.5 at combo 25.
     */
    public double getMultiplier(UUID uuid) {
        int combo = getCombo(uuid);
        return 1.0 + 0.02 * Math.min(combo, 25);
    }

    /**
     * Returns a formatted combo display string for use in action bars / messages.
     * Shows "§e⚡ §f{n}x Combo" when n >= 3, otherwise an empty string.
     */
    public String getComboDisplay(UUID uuid) {
        int n = getCombo(uuid);
        if (n >= 3) {
            return "§e⚡ §f" + n + "x Combo";
        }
        return "";
    }

    /**
     * Removes the player from the in-memory cache (called on disconnect).
     */
    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Clears combo data for all players (e.g. on plugin shutdown or arena reset).
     */
    public void resetAll() {
        cache.clear();
    }

    // ── DB helpers ─────────────────────────────────────────────────────────────

    private int loadFromDb(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT combo FROM smash_combo WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("combo");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("ComboManager.loadFromDb: " + e.getMessage());
        }
        return 0;
    }

    private void saveAsync(UUID uuid, int value) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDb().getConnection();
                 PreparedStatement st = c.prepareStatement("""
                     INSERT INTO smash_combo (uuid, combo) VALUES (?, ?)
                     ON DUPLICATE KEY UPDATE combo = VALUES(combo)
                     """)) {
                st.setString(1, uuid.toString());
                st.setInt(2, value);
                st.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("ComboManager.saveAsync: " + e.getMessage());
            }
        });
    }
}
