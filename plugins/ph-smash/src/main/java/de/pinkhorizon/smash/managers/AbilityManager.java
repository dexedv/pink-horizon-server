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
            new long[]{120, 300, 600, 1100, 1900, 3100, 5000, 8000, 12500, 19000}),
        IMMUN        ("§d🛡 Immunität",     "1% Effekt-Resist/Lv (max 50%)",       50,
            new long[]{
                100, 115, 135, 155, 180, 205, 240, 275, 315, 360,
                415, 480, 550, 635, 730, 840, 965, 1110, 1280, 1470,
                1690, 1940, 2230, 2565, 2950, 3390, 3900, 4485, 5160, 5935,
                6825, 7850, 9030, 10385, 11940, 13730, 15790, 18160, 20885, 24015,
                27620, 31760, 36525, 42005, 48305, 55550, 63880, 73465, 84485, 97160
            }),

        // ── Schwert-Fähigkeiten ───────────────────────────────────────────
        KRITISCH      ("§c⚔ Kritischer Treffer", "3% Krit-Chance/Lv → 2,5× Schwert-Schaden",      15,
            new long[]{300, 450, 650, 950, 1400, 2000, 2900, 4200, 6100, 8800, 12700, 18400, 26600, 38500, 55700}),
        HINRICHTUNG   ("§4⚔ Hinrichtung",        "+10% Schwert-Schaden/Lv wenn Boss < 25% HP",     10,
            new long[]{400, 900, 1700, 3000, 5000, 8200, 13000, 20000, 31000, 47000}),
        WIRBELWIND    ("§6⚔ Wirbelwind",          "5% Chance/Lv: Boss zweimal treffen (50% Schaden)", 10,
            new long[]{300, 700, 1400, 2500, 4200, 6800, 11000, 17500, 27500, 43000}),

        // ── Bogen-Fähigkeiten ─────────────────────────────────────────────
        BOGENSTAERKE  ("§a🏹 Bogenstärke",        "+5% Bogen-Schaden/Lv",                           15,
            new long[]{300, 450, 650, 950, 1400, 2000, 2900, 4200, 6100, 8800, 12700, 18400, 26600, 38500, 55700}),
        MEHRFACHSCHUSS("§e🏹 Mehrfachschuss",      "4% Chance/Lv: 2. Pfeil (80% Schaden)",           10,
            new long[]{350, 800, 1600, 2800, 4600, 7500, 12000, 19000, 30000, 46000}),
        GIFTPFEIL     ("§2🏹 Giftpfeil",           "5% Chance/Lv: Gift-DOT (3× 5% des Treffers)",    10,
            new long[]{250, 600, 1200, 2200, 3700, 6000, 9700, 15500, 24500, 38500}),

        // ── Axt-Fähigkeiten ───────────────────────────────────────────────
        BLUTUNG        ("§c🩸 Blutung",         "5% Chance/Lv: Blutungs-DOT (N Ticks × 6% Schaden)", 15,
            new long[]{300, 450, 650, 950, 1400, 2000, 2900, 4200, 6100, 8800, 12700, 18400, 26600, 38500, 55700}),
        KLAFFENDE_WUNDE("§4🗡 Klaffende Wunde", "+10% Blut-DOT-Schaden/Lv (Multiplikator)",           10,
            new long[]{400, 900, 1700, 3000, 5000, 8200, 13000, 20000, 31000, 47000}),
        TIEFE_HIEBE    ("§8⏱ Tiefe Hiebe",      "+1 Tick/Lv Blutungsdauer (Base 3 Ticks)",             10,
            new long[]{300, 700, 1400, 2500, 4200, 6800, 11000, 17500, 27500, 43000});

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

    /** Chance (0.0–0.50) negative Boss-Effekte zu blocken. */
    public double getEffectResistChance(UUID uuid) {
        return Math.min(getLevel(uuid, AbilityType.IMMUN), AbilityType.IMMUN.maxLevel) * 0.01;
    }

    // ── Schwert-Fähigkeiten ────────────────────────────────────────────────

    /** Kritische-Treffer-Chance (max 45% bei Lv 15). */
    public double getCritChance(UUID uuid) {
        return Math.min(getLevel(uuid, AbilityType.KRITISCH) * 0.03, 0.45);
    }

    /** Zusatz-Multiplikator bei Boss < 25% HP. z.B. Lv10 = +1.0 (doppelter Schaden). */
    public double getExecuteBonus(UUID uuid) {
        return getLevel(uuid, AbilityType.HINRICHTUNG) * 0.10;
    }

    /** Wirbelwind-Chance auf Doppel-Treffer (50% Schaden, max 50% bei Lv 10). */
    public double getWhirlwindChance(UUID uuid) {
        return Math.min(getLevel(uuid, AbilityType.WIRBELWIND) * 0.05, 0.50);
    }

    // ── Bogen-Fähigkeiten ─────────────────────────────────────────────────

    /** Bogen-Schaden-Multiplikator (1.0 = kein Bonus). */
    public double getBowPowerMultiplier(UUID uuid) {
        return 1.0 + getLevel(uuid, AbilityType.BOGENSTAERKE) * 0.05;
    }

    /** Chance auf zweiten Pfeil (80% Schaden, max 40% bei Lv 10). */
    public double getMultishotChance(UUID uuid) {
        return Math.min(getLevel(uuid, AbilityType.MEHRFACHSCHUSS) * 0.04, 0.40);
    }

    /** Chance auf Gift-DOT (3 Ticks à 5% des Treffers, max 50% bei Lv 10). */
    public double getPoisonChance(UUID uuid) {
        return Math.min(getLevel(uuid, AbilityType.GIFTPFEIL) * 0.05, 0.50);
    }

    // ── Axt-Fähigkeiten ────────────────────────────────────────────────────

    /** Blutungs-Chance (max 75% bei Lv 15). */
    public double getBlutungChance(UUID uuid) {
        return Math.min(getLevel(uuid, AbilityType.BLUTUNG) * 0.05, 0.75);
    }

    /** Blut-DOT-Schaden-Multiplikator (1.0 = kein Bonus). Lv10 = 2.0× */
    public double getKlaffendeWundeFactor(UUID uuid) {
        return 1.0 + getLevel(uuid, AbilityType.KLAFFENDE_WUNDE) * 0.10;
    }

    /** Anzahl Blutungs-Ticks (Base 3 + Level, max 13 bei Lv 10). */
    public int getBlutungTicks(UUID uuid) {
        return 3 + getLevel(uuid, AbilityType.TIEFE_HIEBE);
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
