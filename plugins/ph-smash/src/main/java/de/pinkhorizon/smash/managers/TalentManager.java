package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class TalentManager {

    // -------------------------------------------------------------------------
    // Talent type definitions
    // -------------------------------------------------------------------------

    public enum TalentType {
        TREASURE_HUNTER("treasure_hunter", "§a☆ Schatzjäger",   "§7+8% Loot-Chance/Lv",         5, new int[]{1,  2,  4,  8, 15}),
        IRON_HEART      ("iron_heart",      "§c❤ Eisernes Herz",  "§7+8 Max-HP/Lv",                5, new int[]{1,  2,  5, 10, 20}),
        FAST_HANDS      ("fast_hands",      "§e⚡ Schnellhänder",  "§7+5% Angriffsgeschw./Lv",      5, new int[]{1,  3,  6, 10, 18}),
        COIN_MASTER     ("coin_master",     "§6★ Münzmeister",    "§7+10% Münz-Gewinn/Lv",         5, new int[]{2,  4,  8, 15, 25}),
        BOSS_SLAYER     ("boss_slayer",     "§4⚔ Bossjäger",      "§7+3% Dmg vs Boss>Lv50/Lv",    5, new int[]{2,  5, 10, 18, 30});

        public final String id;
        public final String displayName;
        public final String effectDesc;
        public final int    maxLevel;
        public final int[]  bossCoresCost;

        TalentType(String id, String displayName, String effectDesc, int maxLevel, int[] bossCoresCost) {
            this.id           = id;
            this.displayName  = displayName;
            this.effectDesc   = effectDesc;
            this.maxLevel     = maxLevel;
            this.bossCoresCost = bossCoresCost;
        }

        /**
         * Returns the boss core cost for the next upgrade from currentLevel,
         * or -1 if already at max level.
         */
        public int nextCost(int currentLevel) {
            if (currentLevel >= maxLevel) return -1;
            return bossCoresCost[currentLevel]; // index = current level (0-based cost for next)
        }
    }

    // -------------------------------------------------------------------------

    private final PHSmash plugin;

    public TalentManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the current level of the given talent for the player (0 if not unlocked).
     */
    public int getLevel(UUID uuid, TalentType type) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT level FROM smash_talents WHERE uuid = ? AND talent_id = ?")) {
            st.setString(1, uuid.toString());
            st.setString(2, type.id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("level");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("TalentManager.getLevel: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Attempts to upgrade the given talent for the player by consuming boss cores.
     * Returns true on success, false if already maxed or insufficient resources.
     */
    public boolean tryUpgrade(Player player, TalentType type) {
        UUID uuid       = player.getUniqueId();
        int  current    = getLevel(uuid, type);
        int  cost       = type.nextCost(current);

        if (cost < 0) {
            player.sendMessage("§c✗ " + type.displayName + " §cist bereits auf Max-Level!");
            return false;
        }

        boolean consumed = plugin.getLootManager().consume(uuid, LootItem.BOSS_CORE, cost);
        if (!consumed) {
            int have = plugin.getLootManager().getQuantity(uuid, LootItem.BOSS_CORE);
            player.sendMessage("§c✗ Nicht genug Boss-Kerne! Benötigt: §f" + cost
                + " §c| Besitzt: §f" + have);
            return false;
        }

        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_talents (uuid, talent_id, level) VALUES (?, ?, 1)
                 ON DUPLICATE KEY UPDATE level = level + 1
                 """)) {
            st.setString(1, uuid.toString());
            st.setString(2, type.id);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("TalentManager.tryUpgrade: " + e.getMessage());
            player.sendMessage("§c✗ Upgrade fehlgeschlagen (Datenbankfehler).");
            return false;
        }

        int newLevel = getLevel(uuid, type);
        player.sendMessage("§a✔ " + type.displayName + " §7auf Level §f" + newLevel + " §7erhöht!");
        return true;
    }

    // -------------------------------------------------------------------------
    // Bonus getters
    // -------------------------------------------------------------------------

    /** +8% loot chance per TREASURE_HUNTER level */
    public double getTreasureBonus(UUID uuid) {
        return getLevel(uuid, TalentType.TREASURE_HUNTER) * 0.08;
    }

    /** +8 max HP per IRON_HEART level */
    public double getIronHeartBonus(UUID uuid) {
        return getLevel(uuid, TalentType.IRON_HEART) * 8.0;
    }

    /** +5% attack speed per FAST_HANDS level */
    public double getFastHandsBonus(UUID uuid) {
        return getLevel(uuid, TalentType.FAST_HANDS) * 0.05;
    }

    /** +10% coin gain per COIN_MASTER level */
    public double getCoinMasterBonus(UUID uuid) {
        return getLevel(uuid, TalentType.COIN_MASTER) * 0.10;
    }

    /** +3% damage vs boss level > 50, per BOSS_SLAYER level (0 otherwise) */
    public double getBossSlayerBonus(UUID uuid, int bossLevel) {
        if (bossLevel > 50) {
            return getLevel(uuid, TalentType.BOSS_SLAYER) * 0.03;
        }
        return 0.0;
    }
}
