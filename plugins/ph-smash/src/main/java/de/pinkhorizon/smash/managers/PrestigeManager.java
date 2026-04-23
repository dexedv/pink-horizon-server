package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PrestigeManager {

    private final PHSmash plugin;

    public PrestigeManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the current prestige count for the given player, or 0 if none.
     */
    public int getPrestige(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT prestige FROM smash_prestige WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("prestige");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("PrestigeManager.getPrestige: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Returns true if the player's personal boss level is >= 999.
     */
    public boolean canPrestige(Player player) {
        int level = plugin.getPlayerDataManager().getPersonalBossLevel(player.getUniqueId());
        return level >= 999;
    }

    /**
     * Attempts to prestige the player. Requires personal_level >= 999.
     * On success: increments prestige, resets level to 1, clears upgrades + abilities.
     * Returns true on success, false if conditions are not met.
     */
    public boolean doPrestige(Player player) {
        UUID uuid = player.getUniqueId();

        if (!canPrestige(player)) {
            player.sendMessage("§c✗ Du brauchst Boss-Level 999 für Prestige!");
            return false;
        }

        try (Connection c = plugin.getDb().getConnection()) {
            // 1. Increment prestige
            try (PreparedStatement st = c.prepareStatement(
                    "INSERT INTO smash_prestige (uuid, prestige) VALUES (?, 1) " +
                    "ON DUPLICATE KEY UPDATE prestige = prestige + 1")) {
                st.setString(1, uuid.toString());
                st.executeUpdate();
            }

            // 2. Reset personal level to 1
            plugin.getPlayerDataManager().setPersonalBossLevel(uuid, 1);

            // 3. Delete all upgrades
            try (PreparedStatement st = c.prepareStatement(
                    "DELETE FROM smash_upgrades WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                st.executeUpdate();
            }

            // 4. Delete all abilities
            try (PreparedStatement st = c.prepareStatement(
                    "DELETE FROM smash_abilities WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                st.executeUpdate();
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("PrestigeManager.doPrestige: " + e.getMessage());
            player.sendMessage("§c✗ Prestige fehlgeschlagen (Datenbankfehler).");
            return false;
        }

        int newPrestige = getPrestige(uuid);
        player.sendMessage("§d✦ §7PRESTIGE §d" + newPrestige + "§7! Upgrades zurückgesetzt. +5% permanenter Schaden.");
        return true;
    }

    /**
     * Returns a damage multiplier of 1.0 + 0.05 per prestige level.
     */
    public double getPrestigeMultiplier(UUID uuid) {
        return 1.0 + 0.05 * getPrestige(uuid);
    }

    /**
     * Returns a visual prestige display string with diamond symbols (up to 5),
     * or an empty string if prestige is 0.
     */
    public String getPrestigeDisplay(UUID uuid) {
        int prestige = getPrestige(uuid);
        if (prestige <= 0) return "";
        return "§d" + "✦".repeat(Math.min(prestige, 5));
    }
}
