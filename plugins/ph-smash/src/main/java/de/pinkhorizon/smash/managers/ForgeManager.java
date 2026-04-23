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

/**
 * Manages the forge enchantment system.
 * Players spend loot items to craft charges for combat enchants.
 * Charges are consumed (decremented by 1 for each active enchant) after every boss kill.
 *
 * DB table (create if not present):
 *   smash_forge (uuid CHAR(36), forge_id VARCHAR(32), charges INT DEFAULT 0,
 *                PRIMARY KEY(uuid, forge_id))
 */
public class ForgeManager {

    // -------------------------------------------------------------------------
    // Inner enum
    // -------------------------------------------------------------------------

    public enum ForgeEnchant {

        SHARPNESS  ("schärfe",     "§c⚔ Schärfe V",        "§7+50% Schaden §8(Schwert & Axt)",        Material.DIAMOND_SWORD,  10, LootItem.IRON_FRAGMENT,  20),
        POWER      ("kraft",       "§a⚡ Kraft V",           "§7+50% Schaden §8(Bogen & Feuerball)",    Material.BOW,            10, LootItem.IRON_FRAGMENT,  20),
        FIRE_ASPECT("feuer",       "§6🔥 Feuer II",          "§7Setzt Boss in Brand bei Treffer",  Material.BLAZE_ROD,      10, LootItem.GOLD_FRAGMENT,  15),
        KNOCKBACK  ("stoss",       "§b↩ Stoß II",           "§7Schlägt Boss zurück",              Material.STICK,          10, LootItem.GOLD_FRAGMENT,  10),
        LIFEDRAIN  ("lebensraub",  "§5♥ Lebensraub III",    "§7+15% extra Heilung bei Treffer",   Material.GHAST_TEAR,     10, LootItem.DIAMOND_SHARD,   5);

        public final String   id;
        public final String   displayName;
        public final String   desc;
        public final Material icon;
        public final int      chargesPerCraft;
        public final LootItem costItem;
        public final int      costAmount;

        ForgeEnchant(String id, String displayName, String desc, Material icon,
                     int chargesPerCraft, LootItem costItem, int costAmount) {
            this.id              = id;
            this.displayName     = displayName;
            this.desc            = desc;
            this.icon            = icon;
            this.chargesPerCraft = chargesPerCraft;
            this.costItem        = costItem;
            this.costAmount      = costAmount;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PHSmash plugin;

    public ForgeManager(PHSmash plugin) {
        this.plugin = plugin;
        createTable();
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    private void createTable() {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS smash_forge (
                     uuid      CHAR(36)    NOT NULL,
                     forge_id  VARCHAR(32) NOT NULL,
                     charges   INT         NOT NULL DEFAULT 0,
                     PRIMARY KEY (uuid, forge_id)
                 )
                 """)) {
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("ForgeManager.createTable: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Core methods
    // -------------------------------------------------------------------------

    /**
     * Returns the current charge count for the given enchant.
     */
    public int getCharges(UUID uuid, ForgeEnchant enchant) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT charges FROM smash_forge WHERE uuid = ? AND forge_id = ?")) {
            st.setString(1, uuid.toString());
            st.setString(2, enchant.id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("charges");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("ForgeManager.getCharges: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Attempts to craft charges for the given enchant.
     * Consumes the required loot items and adds {@code chargesPerCraft} charges.
     *
     * @return true if crafting succeeded, false if the player lacks materials
     */
    public boolean craft(Player player, ForgeEnchant enchant) {
        UUID uuid = player.getUniqueId();
        boolean consumed = plugin.getLootManager().consume(uuid, enchant.costItem, enchant.costAmount);
        if (!consumed) {
            player.sendMessage("§cNicht genug " + enchant.costItem.color
                    + enchant.costItem.displayName + "§c. Benötigt: §f"
                    + enchant.costAmount + "x");
            return false;
        }

        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_forge (uuid, forge_id, charges) VALUES (?, ?, ?)
                 ON DUPLICATE KEY UPDATE charges = charges + VALUES(charges)
                 """)) {
            st.setString(1, uuid.toString());
            st.setString(2, enchant.id);
            st.setInt(3, enchant.chargesPerCraft);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("ForgeManager.craft: " + e.getMessage());
            // Loot was already consumed — best effort, no rollback
            return false;
        }

        player.sendMessage("§a✔ §f" + enchant.displayName
                + " §7— §e+" + enchant.chargesPerCraft + " Ladungen");
        return true;
    }

    /**
     * Decrements the charge count for a single enchant by 1, floored at 0.
     */
    public void consumeCharge(UUID uuid, ForgeEnchant enchant) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 UPDATE smash_forge
                 SET charges = GREATEST(0, charges - 1)
                 WHERE uuid = ? AND forge_id = ?
                 """)) {
            st.setString(1, uuid.toString());
            st.setString(2, enchant.id);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("ForgeManager.consumeCharge: " + e.getMessage());
        }
    }

    /**
     * Returns true if the player has at least 1 charge remaining for the given enchant.
     */
    public boolean isActive(UUID uuid, ForgeEnchant enchant) {
        return getCharges(uuid, enchant) > 0;
    }

    /**
     * Called after a boss is defeated by the given player.
     * Decrements charges for every ForgeEnchant that is currently active.
     */
    public void onBossDefeated(UUID uuid) {
        for (ForgeEnchant enchant : ForgeEnchant.values()) {
            if (isActive(uuid, enchant)) {
                consumeCharge(uuid, enchant);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Combat convenience methods
    // -------------------------------------------------------------------------

    /** Returns 1.5 if SHARPNESS is active for this player, 1.0 otherwise. */
    public double getSharpnessMultiplier(UUID uuid) {
        return isActive(uuid, ForgeEnchant.SHARPNESS) ? 1.5 : 1.0;
    }

    /** Returns 1.5 if POWER is active for this player, 1.0 otherwise. */
    public double getPowerMultiplier(UUID uuid) {
        return isActive(uuid, ForgeEnchant.POWER) ? 1.5 : 1.0;
    }

    /** Returns true if FIRE_ASPECT is active for this player. */
    public boolean hasFireAspect(UUID uuid) {
        return isActive(uuid, ForgeEnchant.FIRE_ASPECT);
    }

    /** Returns true if KNOCKBACK is active for this player. */
    public boolean hasKnockback(UUID uuid) {
        return isActive(uuid, ForgeEnchant.KNOCKBACK);
    }

    /** Returns 0.15 if LIFEDRAIN is active for this player, 0.0 otherwise. */
    public double getLifedrainBonus(UUID uuid) {
        return isActive(uuid, ForgeEnchant.LIFEDRAIN) ? 0.15 : 0.0;
    }
}
