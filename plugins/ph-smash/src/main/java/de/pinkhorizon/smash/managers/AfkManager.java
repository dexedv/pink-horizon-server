package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

public class AfkManager {

    // Belohnungen pro Sekunde AFK-Zeit (bei 2h = 7200s ohne Prestige)
    // Coins: 20.000 / 7200 ≈ 2.78/s
    // Eisen:    500 / 7200 ≈ 0.069/s
    // Gold:     150 / 7200 ≈ 0.021/s
    // Diamant:   30 / 7200 ≈ 0.0042/s
    // Kern:       7 / 7200 ≈ 0.00097/s
    private static final double COINS_PER_SEC   = 20000.0 / 7200.0;
    private static final double IRON_PER_SEC    =   500.0 / 7200.0;
    private static final double GOLD_PER_SEC    =   150.0 / 7200.0;
    private static final double DIAMOND_PER_SEC =    30.0 / 7200.0;
    private static final double CORE_PER_SEC    =     7.0 / 7200.0;

    // Limits
    private static final long MAX_SECONDS_PER_KILL = 300L;    // max 5 Min pro Kill
    private static final long MAX_STORED_SECONDS   = 7200L;   // max 2h gespeichert

    private final PHSmash plugin;

    public AfkManager(PHSmash plugin) {
        this.plugin = plugin;
        createTable();
    }

    private void createTable() {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS smash_afk (
                     uuid        CHAR(36) NOT NULL PRIMARY KEY,
                     afk_seconds BIGINT   NOT NULL DEFAULT 0,
                     farm_start  BIGINT   DEFAULT NULL
                 )
                 """)) {
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("AfkManager.createTable: " + e.getMessage());
        }
    }

    /** Gespeicherte AFK-Zeit in Sekunden. */
    public long getAfkSeconds(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT afk_seconds FROM smash_afk WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getLong("afk_seconds");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AfkManager.getAfkSeconds: " + e.getMessage());
        }
        return 0;
    }

    /** Prüft ob der Spieler gerade am Farmen ist (farm_start gesetzt). */
    public boolean isFarming(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT farm_start FROM smash_afk WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    long v = rs.getLong("farm_start");
                    return !rs.wasNull() && v > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AfkManager.isFarming: " + e.getMessage());
        }
        return false;
    }

    /**
     * Nach einem Boss-Kill: AFK-Zeit gutschreiben.
     * Formel: min(MAX_PER_KILL, 30 + bossLevel) Sekunden.
     */
    public void onBossKill(UUID uuid, int bossLevel) {
        long earned = Math.min(MAX_SECONDS_PER_KILL, 30L + bossLevel);
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO smash_afk (uuid, afk_seconds) VALUES (?, ?) " +
                 "ON DUPLICATE KEY UPDATE afk_seconds = LEAST(afk_seconds + VALUES(afk_seconds), " + MAX_STORED_SECONDS + ")")) {
            st.setString(1, uuid.toString());
            st.setLong(2, earned);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("AfkManager.onBossKill: " + e.getMessage());
        }
    }

    /**
     * AFK-Farm starten: setzt farm_start auf jetzt.
     * Spieler sollte danach offline gehen.
     * @return true wenn gestartet, false wenn keine Zeit vorhanden
     */
    public boolean startFarming(Player player) {
        UUID uuid    = player.getUniqueId();
        long seconds = getAfkSeconds(uuid);
        if (seconds <= 0) {
            player.sendMessage("§c✗ §7Keine AFK-Zeit gespeichert! Töte mehr Bosse.");
            return false;
        }
        if (isFarming(uuid)) {
            player.sendMessage("§e⏳ §7AFK-Farm läuft bereits. Geh offline um sie zu nutzen.");
            return false;
        }
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE smash_afk SET farm_start = ? WHERE uuid = ?")) {
            st.setLong(1, System.currentTimeMillis());
            st.setString(2, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("AfkManager.startFarming: " + e.getMessage());
            return false;
        }
        player.sendMessage("§b§l⏰ AFK-Farm gestartet!");
        player.sendMessage("§7Gespeicherte Zeit: §b" + formatTime(seconds));
        player.sendMessage("§7Du kannst jetzt offline gehen. Beim nächsten Login erhältst du deine Belohnungen.");
        return true;
    }

    /**
     * Beim Login aufrufen (async). Berechnet Belohnungen für die offline verbrachte Zeit
     * und schreibt sie dem Spieler gut.
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        long[] data = getFarmData(uuid);
        if (data == null) return;
        long afkSeconds = data[0];
        long farmStart  = data[1];
        if (farmStart <= 0) return;  // nicht am Farmen

        long now     = System.currentTimeMillis();
        long elapsed = (now - farmStart) / 1000L;
        long spent   = Math.min(elapsed, afkSeconds);

        // farm_start immer zuerst zurücksetzen (auch bei spent=0)
        clearFarmStart(uuid);

        if (spent <= 0) return;

        // Belohnungen berechnen (skaliert mit Prestige)
        int    prestige = plugin.getPrestigeManager().getPrestige(uuid);
        double scale    = 1.0 + prestige * 0.5;
        long   coins    = (long) (spent * COINS_PER_SEC * scale);
        int    iron     = (int)  (spent * IRON_PER_SEC);
        int    gold     = (int)  (spent * GOLD_PER_SEC);
        int    diamond  = (int)  (spent * DIAMOND_PER_SEC);
        int    core     = (int)  (spent * CORE_PER_SEC);
        long   remaining = afkSeconds - spent;

        // Belohnungen gutschreiben
        if (coins   > 0) plugin.getCoinManager().addCoins(uuid, coins);
        if (iron    > 0) plugin.getLootManager().addLoot(uuid, LootManager.LootItem.IRON_FRAGMENT, iron);
        if (gold    > 0) plugin.getLootManager().addLoot(uuid, LootManager.LootItem.GOLD_FRAGMENT, gold);
        if (diamond > 0) plugin.getLootManager().addLoot(uuid, LootManager.LootItem.DIAMOND_SHARD, diamond);
        if (core    > 0) plugin.getLootManager().addLoot(uuid, LootManager.LootItem.BOSS_CORE, core);

        // Verbleibende Zeit in DB aktualisieren
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE smash_afk SET afk_seconds = ? WHERE uuid = ?")) {
            st.setLong(1, remaining);
            st.setString(2, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("AfkManager.onPlayerJoin update: " + e.getMessage());
        }

        // Meldung im Main-Thread anzeigen
        final long fSpent = spent, fRemaining = remaining, fCoins = coins;
        final int  fIron = iron, fGold = gold, fDiamond = diamond, fCore = core;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage("§b§l⏰ AFK-Farm Ergebnis §8(§7" + formatTime(fSpent) + "§8)§7:");
            player.sendMessage("  §6+" + fCoins + " §7Münzen");
            if (fIron    > 0) player.sendMessage("  §7+" + fIron    + " §7Eisenfragmente");
            if (fGold    > 0) player.sendMessage("  §6+" + fGold    + " §7Goldfragmente");
            if (fDiamond > 0) player.sendMessage("  §b+" + fDiamond + " §7Diamantscherben");
            if (fCore    > 0) player.sendMessage("  §5+" + fCore    + " §7Boss-Kerne");
            player.sendMessage(fRemaining > 0
                ? "§7Verbleibende Zeit: §b" + formatTime(fRemaining)
                : "§7AFK-Zeit aufgebraucht. Töte mehr Bosse!");
        });
    }

    private void clearFarmStart(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE smash_afk SET farm_start = NULL WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("AfkManager.clearFarmStart: " + e.getMessage());
        }
    }

    /** Gibt [afk_seconds, farm_start] zurück, oder null wenn kein Eintrag. */
    private long[] getFarmData(UUID uuid) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT afk_seconds, farm_start FROM smash_afk WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    long afkSec    = rs.getLong("afk_seconds");
                    long farmStart = rs.getLong("farm_start");
                    if (rs.wasNull()) farmStart = 0;
                    return new long[]{afkSec, farmStart};
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AfkManager.getFarmData: " + e.getMessage());
        }
        return null;
    }

    /** Formatiert Sekunden in "Xh Xm Xs". */
    public static String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
