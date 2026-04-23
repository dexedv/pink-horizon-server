package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

public class AbilityManager {

    // ── Fähigkeiten-Definition ─────────────────────────────────────────────

    public enum AbilityType {
        BERSERKER    ("§c⚔ Berserker",      "Bei <35% HP: +8% Schaden/Lv",         10,
            new long[]{150, 350, 700, 1300, 2200, 3500, 5500, 8500, 13000, 20000}),
        DODGE        ("§b⚡ Ausweichen",     "4% Ausweich-Chance/Lv (max 40%)",      10,
            new long[]{200, 480, 950, 1800, 3000, 4800, 7500, 12000, 18000, 28000}),
        HEAL_ON_KILL ("§4❤ Boss-Heilung",   "+8% max-HP Heilung beim Kill/Lv",      10,
            new long[]{180, 420, 850, 1600, 2700, 4300, 6800, 10500, 16000, 25000}),
        EXPLOSIVE    ("§6✦ Explosivpfeil",  "7% Chance auf 2× Pfeil-Schaden/Lv",   10,
            new long[]{250, 600, 1200, 2300, 3800, 6000, 9500, 15000, 23000, 35000}),
        COIN_BOOST   ("§e★ Münz-Boost",     "+20% Coins pro Boss-Kill/Lv",          10,
            new long[]{100, 250, 500, 950, 1600, 2600, 4200, 6800, 11000, 17000}),
        REGEN        ("§a◆ Regeneration",   "+1.5 HP alle 5 Sek/Lv",               10,
            new long[]{120, 300, 600, 1100, 1900, 3100, 5000, 8000, 12500, 19000});

        public final String displayName;
        public final String effectDesc;
        public final int    maxLevel;
        public final long[] costs;   // costs[i] = Kosten für Lv i→i+1

        AbilityType(String displayName, String effectDesc, int maxLevel, long[] costs) {
            this.displayName = displayName;
            this.effectDesc  = effectDesc;
            this.maxLevel    = maxLevel;
            this.costs       = costs;
        }

        /** Kosten für nächstes Level. -1 wenn bereits max. */
        public long nextCost(int currentLevel) {
            if (currentLevel >= maxLevel) return -1;
            return costs[currentLevel];
        }
    }

    private final PHSmash plugin;

    public AbilityManager(PHSmash plugin) {
        this.plugin = plugin;
        startRegenTask();
    }

    // ── DB-Zugriff ─────────────────────────────────────────────────────────

    public int getLevel(UUID uuid, AbilityType type) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT level FROM smash_abilities WHERE uuid = ? AND ability_id = ?")) {
            st.setString(1, uuid.toString());
            st.setString(2, type.name());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("level");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AbilityManager.getLevel: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Versucht Upgrade. Zieht Coins ab, erhöht Level.
     * Sync – muss aus Async-Kontext aufgerufen werden.
     * @return true wenn erfolgreich
     */
    public boolean tryUpgrade(Player player, AbilityType type) {
        int  current = getLevel(player.getUniqueId(), type);
        long cost    = type.nextCost(current);
        if (cost < 0) {
            Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage("§c" + type.displayName + " §7ist bereits auf Maximal-Level!"));
            return false;
        }
        if (!plugin.getCoinManager().spendCoins(player.getUniqueId(), cost)) {
            Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage("§cNicht genug Münzen! §8(braucht: §e" + cost + "§8)"));
            return false;
        }
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO smash_abilities (uuid, ability_id, level) VALUES (?, ?, 1)
                 ON DUPLICATE KEY UPDATE level = level + 1
                 """)) {
            st.setString(1, player.getUniqueId().toString());
            st.setString(2, type.name());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("AbilityManager.tryUpgrade: " + e.getMessage());
            return false;
        }
        int nextLevel = current + 1;
        Bukkit.getScheduler().runTask(plugin, () ->
            player.sendMessage("§a✔ " + type.displayName + " §7→ Level §c" + nextLevel));
        return true;
    }

    // ── Stat-Getter ────────────────────────────────────────────────────────

    /** Bonus-Schadensmultiplikator wenn HP < 35%. z.B. 0.08 = +8% pro Level */
    public double getBerserkerBonus(UUID uuid) {
        return getLevel(uuid, AbilityType.BERSERKER) * 0.08;
    }

    /** Dodge-Wahrscheinlichkeit. max 0.40 */
    public double getDodgeChance(UUID uuid) {
        return Math.min(getLevel(uuid, AbilityType.DODGE) * 0.04, 0.40);
    }

    /** Heilung als Anteil der max HP beim Boss-Kill. */
    public double getHealOnKillPercent(UUID uuid) {
        return getLevel(uuid, AbilityType.HEAL_ON_KILL) * 0.08;
    }

    /** Chance auf 2× Pfeil-Schaden. */
    public double getExplosiveChance(UUID uuid) {
        return getLevel(uuid, AbilityType.EXPLOSIVE) * 0.07;
    }

    /** Coin-Multiplikator. */
    public double getCoinMultiplier(UUID uuid) {
        return 1.0 + getLevel(uuid, AbilityType.COIN_BOOST) * 0.20;
    }

    /** HP-Regeneration pro 5 Sekunden. */
    public double getRegenAmount(UUID uuid) {
        return getLevel(uuid, AbilityType.REGEN) * 1.5;
    }

    // ── Regen-Task ─────────────────────────────────────────────────────────

    private void startRegenTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!plugin.getArenaManager().hasArena(p.getUniqueId())) continue;
                double regen = getRegenAmount(p.getUniqueId());
                if (regen <= 0) continue;
                var hpAttr = p.getAttribute(Attribute.MAX_HEALTH);
                if (hpAttr == null) continue;
                double newHp = Math.min(p.getHealth() + regen, hpAttr.getValue());
                p.setHealth(newHp);
            }
        }, 100L, 100L);
    }
}
