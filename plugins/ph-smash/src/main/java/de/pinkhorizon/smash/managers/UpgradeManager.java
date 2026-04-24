package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class UpgradeManager {

    public enum UpgradeType {
        ATTACK    ("attack",    "§c⚔ Angriff",        300),
        DEFENSE   ("defense",   "§a🛡 Verteidigung",   100),
        HEALTH    ("health",    "§e❤ Gesundheit",       60),
        SPEED     ("speed",     "§b⚡ Tempo",            35),
        LIFESTEAL ("lifesteal", "§5⬛ Lebensraub",       25);

        public final String id;
        public final String displayName;
        public final int    maxLevel;

        UpgradeType(String id, String displayName, int maxLevel) {
            this.id          = id;
            this.displayName = displayName;
            this.maxLevel    = maxLevel;
        }
    }

    private static final String MOD_KEY_HP    = "smash_health";
    private static final String MOD_KEY_SPEED = "smash_speed";

    private final PHSmash plugin;

    public UpgradeManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    public int getLevel(UUID uuid, UpgradeType type) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT level FROM smash_upgrades WHERE uuid = ? AND upgrade_id = ?")) {
            st.setString(1, uuid.toString());
            st.setString(2, type.id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("level");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("UpgradeManager.getLevel: " + e.getMessage());
        }
        return 0;
    }

    public Map<UpgradeType, Integer> getAllLevels(UUID uuid) {
        Map<UpgradeType, Integer> map = new EnumMap<>(UpgradeType.class);
        for (UpgradeType t : UpgradeType.values()) map.put(t, getLevel(uuid, t));
        return map;
    }

    /** Gibt den Schaden-Multiplikator zurück (1.0 = kein Bonus) */
    public double getAttackMultiplier(UUID uuid) {
        return 1.0 + 0.08 * getLevel(uuid, UpgradeType.ATTACK);
    }

    /** Gibt den eingehenden Schaden-Multiplikator zurück (<1.0 = weniger Schaden, min 0.25) */
    public double getDefenseMultiplier(UUID uuid) {
        return Math.max(0.25, 1.0 - 0.015 * getLevel(uuid, UpgradeType.DEFENSE));
    }

    public double getLifestealPercent(UUID uuid) {
        return Math.min(0.05 * getLevel(uuid, UpgradeType.LIFESTEAL), 0.50);
    }

    /** Versucht ein Upgrade zu kaufen. Gibt false zurück wenn nicht leistbar. */
    public boolean tryUpgrade(Player player, UpgradeType type) {
        UUID uuid       = player.getUniqueId();
        int  curLevel   = getLevel(uuid, type);
        if (curLevel >= type.maxLevel) {
            player.sendMessage("§cMaximales Level bereits erreicht!");
            return false;
        }
        int nextLevel = curLevel + 1;

        // Kosten berechnen – erst alle prüfen, dann erst konsumieren (kein Partial-Consume)
        Map<LootItem, Integer> cost = getCost(type, nextLevel);
        for (Map.Entry<LootItem, Integer> e : cost.entrySet()) {
            if (plugin.getLootManager().getQuantity(uuid, e.getKey()) < e.getValue()) {
                player.sendMessage("§cNicht genug §f" + e.getKey().displayName
                    + "§c! Benötigt: §f" + e.getValue());
                return false;
            }
        }
        for (Map.Entry<LootItem, Integer> e : cost.entrySet()) {
            plugin.getLootManager().consume(uuid, e.getKey(), e.getValue());
        }

        // Level erhöhen
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_upgrades (uuid, upgrade_id, level) VALUES (?, ?, 1)
                 ON DUPLICATE KEY UPDATE level = level + 1
                 """)) {
            st.setString(1, uuid.toString());
            st.setString(2, type.id);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("UpgradeManager.tryUpgrade: " + e.getMessage());
            return false;
        }

        applyStats(player);
        player.sendMessage("§a✔ §f" + type.displayName + " §7auf Level §6" + nextLevel + " §7aufgewertet!");
        return true;
    }

    /** Wendet alle Upgrade-Stats auf den Spieler an (nach Einloggen oder Kauf). */
    public void applyStats(Player player) {
        UUID uuid = player.getUniqueId();

        // ─ Herzen-Anzeige skalieren (immer 10 Herzen = volle Gesundheit) ──
        player.setHealthScaled(true);
        player.setHealthScale(20.0);

        // ─ Max-HP ──────────────────────────────────────────────────────────
        var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.getModifiers().stream()
                .filter(m -> m.getName().equals(MOD_KEY_HP))
                .toList().forEach(hpAttr::removeModifier);
            double hpBonus = 6.0 * getLevel(uuid, UpgradeType.HEALTH);
            if (hpBonus > 0) {
                hpAttr.addModifier(new AttributeModifier(
                    MOD_KEY_HP, hpBonus, AttributeModifier.Operation.ADD_NUMBER));
            }
        }

        // ─ Speed ───────────────────────────────────────────────────────────
        var speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.getModifiers().stream()
                .filter(m -> m.getName().equals(MOD_KEY_SPEED))
                .toList().forEach(speedAttr::removeModifier);
            double speedBonus = 0.03 * getLevel(uuid, UpgradeType.SPEED);
            if (speedBonus > 0) {
                speedAttr.addModifier(new AttributeModifier(
                    MOD_KEY_SPEED, speedBonus, AttributeModifier.Operation.ADD_SCALAR));
            }
        }
    }

    /** Berechnet die Kosten für ein bestimmtes Level eines Upgrades. */
    public Map<LootItem, Integer> getCost(UpgradeType type, int targetLevel) {
        Map<LootItem, Integer> cost = new EnumMap<>(LootItem.class);
        switch (type) {
            case ATTACK -> {
                // Lv   1- 80: Eisen = lv × 3
                // Lv  81-200: Eisen = 20, Gold = 15
                // Lv 201-300: Gold = 18, Kristall = lv-275 (ab Lv 276, min 1)
                if (targetLevel <= 80) {
                    cost.put(LootItem.IRON_FRAGMENT, targetLevel * 3);
                } else if (targetLevel <= 200) {
                    cost.put(LootItem.IRON_FRAGMENT, 20);
                    cost.put(LootItem.GOLD_FRAGMENT,  15);
                } else {
                    cost.put(LootItem.GOLD_FRAGMENT,  18);
                    cost.put(LootItem.DIAMOND_SHARD,  Math.max(1, targetLevel - 275));
                }
            }
            case DEFENSE -> {
                // Lv  1- 50: Eisen = lv × 2, Gold = lv
                // Lv 51- 80: Eisen = 40, Gold = (lv-50) × 2
                // Lv 81-100: Gold = 40, Kristall = (lv-80) × 2
                if (targetLevel <= 50) {
                    cost.put(LootItem.IRON_FRAGMENT, targetLevel * 2);
                    cost.put(LootItem.GOLD_FRAGMENT,  targetLevel);
                } else if (targetLevel <= 80) {
                    cost.put(LootItem.IRON_FRAGMENT, 40);
                    cost.put(LootItem.GOLD_FRAGMENT,  (targetLevel - 50) * 2);
                } else {
                    cost.put(LootItem.GOLD_FRAGMENT,  40);
                    cost.put(LootItem.DIAMOND_SHARD,  (targetLevel - 80) * 2);
                }
            }
            case HEALTH -> {
                // Lv  1-30: Gold = lv × 3
                // Lv 31-60: Gold = 20, Kristall = lv-50 (ab Lv 51, min 1)
                if (targetLevel <= 30) {
                    cost.put(LootItem.GOLD_FRAGMENT, targetLevel * 3);
                } else {
                    cost.put(LootItem.GOLD_FRAGMENT,  20);
                    cost.put(LootItem.DIAMOND_SHARD,  Math.max(1, targetLevel - 50));
                }
            }
            case SPEED -> {
                // Lv  1-15: Gold = lv × 4, Kristall = lv
                // Lv 16-35: Kristall = (lv-15) × 2, Kern = 1
                if (targetLevel <= 15) {
                    cost.put(LootItem.GOLD_FRAGMENT,  targetLevel * 4);
                    cost.put(LootItem.DIAMOND_SHARD,  targetLevel);
                } else {
                    cost.put(LootItem.DIAMOND_SHARD, (targetLevel - 15) * 2);
                    cost.put(LootItem.BOSS_CORE,      1);
                }
            }
            case LIFESTEAL -> {
                // Lv  1-15: Kristall = lv × 3
                // Lv 16-25: Kristall = 20, Kern = 2
                if (targetLevel <= 15) {
                    cost.put(LootItem.DIAMOND_SHARD, targetLevel * 3);
                } else {
                    cost.put(LootItem.DIAMOND_SHARD, 20);
                    cost.put(LootItem.BOSS_CORE,     2);
                }
            }
        }
        return cost;
    }
}
