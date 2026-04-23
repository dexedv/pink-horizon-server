package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class RuneManager {

    // -------------------------------------------------------------------------
    // Rune type definitions
    // -------------------------------------------------------------------------

    public enum RuneType {
        WAR_RUNE   ("war_rune",    "§c⚔ Kriegsrune",  "§7+30% Schaden für 3 Bosse",   Material.BLAZE_POWDER, "§7Kosten: §f5× §7Eisen-Splitter"),
        SHIELD_RUNE("shield_rune", "§a🛡 Schutzrune",  "§7-20% Schaden für 3 Bosse",   Material.SHIELD,       "§7Kosten: §f5× §7Gold-Splitter"),
        LUCK_RUNE  ("luck_rune",   "§e★ Glücksrune",   "§7+100% Münzen für 3 Bosse",   Material.NETHER_STAR,  "§7Kosten: §f2× §7Boss-Kristall");

        public final String   id;
        public final String   displayName;
        public final String   effectDesc;
        public final Material icon;
        public final String   costDesc;

        RuneType(String id, String displayName, String effectDesc, Material icon, String costDesc) {
            this.id          = id;
            this.displayName = displayName;
            this.effectDesc  = effectDesc;
            this.icon        = icon;
            this.costDesc    = costDesc;
        }
    }

    // -------------------------------------------------------------------------

    private final PHSmash plugin;

    public RuneManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the number of charges for the given rune type.
     */
    public int getCharges(UUID uuid, RuneType type) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT charges FROM smash_runes WHERE uuid = ? AND rune_id = ?")) {
            st.setString(1, uuid.toString());
            st.setString(2, type.id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("charges");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("RuneManager.getCharges: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Attempts to craft the given rune by consuming the required materials.
     * On success, adds 3 charges. Returns true on success.
     */
    public boolean craftRune(Player player, RuneType type, PHSmash plugin) {
        UUID uuid = player.getUniqueId();

        boolean consumed;
        switch (type) {
            case WAR_RUNE -> {
                consumed = plugin.getLootManager().consume(uuid, LootItem.IRON_FRAGMENT, 5);
                if (!consumed) {
                    player.sendMessage("§c✗ Nicht genug Eisen-Splitter! Benötigt: §f5");
                    return false;
                }
            }
            case SHIELD_RUNE -> {
                consumed = plugin.getLootManager().consume(uuid, LootItem.GOLD_FRAGMENT, 5);
                if (!consumed) {
                    player.sendMessage("§c✗ Nicht genug Gold-Splitter! Benötigt: §f5");
                    return false;
                }
            }
            case LUCK_RUNE -> {
                consumed = plugin.getLootManager().consume(uuid, LootItem.DIAMOND_SHARD, 2);
                if (!consumed) {
                    player.sendMessage("§c✗ Nicht genug Boss-Kristalle! Benötigt: §f2");
                    return false;
                }
            }
            default -> {
                return false;
            }
        }

        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_runes (uuid, rune_id, charges) VALUES (?, ?, 3)
                 ON DUPLICATE KEY UPDATE charges = charges + 3
                 """)) {
            st.setString(1, uuid.toString());
            st.setString(2, type.id);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("RuneManager.craftRune: " + e.getMessage());
            player.sendMessage("§c✗ Schmieden fehlgeschlagen (Datenbankfehler).");
            return false;
        }

        player.sendMessage("§a✔ " + type.displayName + " §7geschmiedet! §f+3 Ladungen.");
        return true;
    }

    /**
     * Decrements the charges of all active runes by 1 (down to 0) after a boss kill.
     */
    public void onBossDefeated(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE smash_runes SET charges = GREATEST(0, charges - 1) " +
                 "WHERE uuid = ? AND charges > 0")) {
            st.setString(1, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("RuneManager.onBossDefeated: " + e.getMessage());
        }
    }

    /**
     * Returns 1.30 if WAR_RUNE is active (charges > 0), else 1.0.
     */
    public double getWarRuneMultiplier(UUID uuid) {
        return getCharges(uuid, RuneType.WAR_RUNE) > 0 ? 1.30 : 1.0;
    }

    /**
     * Returns 0.80 if SHIELD_RUNE is active (charges > 0), else 1.0.
     */
    public double getShieldRuneMultiplier(UUID uuid) {
        return getCharges(uuid, RuneType.SHIELD_RUNE) > 0 ? 0.80 : 1.0;
    }

    /**
     * Returns 2.0 if LUCK_RUNE is active (charges > 0), else 1.0.
     */
    public double getLuckRuneMultiplier(UUID uuid) {
        return getCharges(uuid, RuneType.LUCK_RUNE) > 0 ? 2.0 : 1.0;
    }
}
