package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Material;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Trackt abgebaute Blöcke und Erzfunde pro Spieler für die XRay-Erkennung.
 *
 * DB-Tabellen:
 *   sv_mining_log    – einzelne Erzfunde (ore_type, Koordinaten, Timestamp)
 *   sv_mining_blocks – Aggregat-Zähler (total_blocks, ores_found) pro Spieler
 *
 * Block-Zähler werden in-memory gehalten und alle 5 Minuten + beim Logout geflusht.
 */
public class MiningStatsManager {

    private final PHSurvival plugin;

    /** In-memory Zähler: UUID → noch nicht in DB geschriebene Blöcke */
    private final Map<UUID, AtomicLong> pendingBlocks = new ConcurrentHashMap<>();

    public MiningStatsManager(PHSurvival plugin) {
        this.plugin = plugin;
        createTables();
        // Flush alle 5 Minuten (6000 Ticks)
        plugin.getServer().getScheduler()
            .runTaskTimerAsynchronously(plugin, this::flushAll, 6000L, 6000L);
    }

    // ── DB ────────────────────────────────────────────────────────────────

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    private void createTables() {
        try (Connection c = con(); Statement s = c.createStatement()) {
            s.execute(
                "CREATE TABLE IF NOT EXISTS sv_mining_log (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  player_uuid VARCHAR(36) NOT NULL," +
                "  ore_type VARCHAR(40) NOT NULL," +
                "  x INT NOT NULL, y INT NOT NULL, z INT NOT NULL," +
                "  mined_at BIGINT NOT NULL," +
                "  INDEX idx_player (player_uuid)," +
                "  INDEX idx_time   (mined_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            s.execute(
                "CREATE TABLE IF NOT EXISTS sv_mining_blocks (" +
                "  player_uuid VARCHAR(36) PRIMARY KEY," +
                "  total_blocks BIGINT NOT NULL DEFAULT 0," +
                "  ores_found   INT    NOT NULL DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        } catch (SQLException e) {
            plugin.getLogger().warning("[Mining] Tabellen: " + e.getMessage());
        }
    }

    // ── Öffentliche API ───────────────────────────────────────────────────

    /**
     * Einen Erzfund asynchron loggen und den ores_found-Zähler erhöhen.
     */
    public void logOre(UUID uuid, String oreType, int x, int y, int z) {
        long now = System.currentTimeMillis();
        // Pending-Blöcke sofort drainieren, damit total_blocks beim Erz-Log aktuell ist
        AtomicLong counter = pendingBlocks.get(uuid);
        long pending = counter != null ? counter.getAndSet(0) : 0;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = con();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sv_mining_log (player_uuid,ore_type,x,y,z,mined_at) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, oreType);
                ps.setInt(3, x);
                ps.setInt(4, y);
                ps.setInt(5, z);
                ps.setLong(6, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[Mining] logOre: " + e.getMessage());
            }
            // Aggregat-Tabelle: ores_found +1, ggf. pending blocks mitspeichern
            try (Connection c = con();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sv_mining_blocks (player_uuid,total_blocks,ores_found) VALUES(?,?,1)" +
                     " ON DUPLICATE KEY UPDATE total_blocks = total_blocks + ?, ores_found = ores_found + 1")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, pending);
                ps.setLong(3, pending);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[Mining] oreCount: " + e.getMessage());
            }
        });
    }

    /**
     * Abgebaute Blöcke in den In-Memory-Zähler schreiben.
     * Wird auf dem Haupt-Thread aus dem BlockBreakListener aufgerufen.
     */
    public void incrementBlocks(UUID uuid, long count) {
        pendingBlocks.computeIfAbsent(uuid, k -> new AtomicLong()).addAndGet(count);
    }

    /** Spieler ausgeloggt: seinen Zähler sofort flushen. */
    public void flushPlayer(UUID uuid) {
        AtomicLong counter = pendingBlocks.remove(uuid);
        if (counter == null) return;
        long blocks = counter.get();
        if (blocks == 0) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> flushToDB(uuid.toString(), blocks));
    }

    /** Alle offenen Zähler flushen (Timer + onDisable). */
    public void flushAll() {
        for (Map.Entry<UUID, AtomicLong> entry : pendingBlocks.entrySet()) {
            long blocks = entry.getValue().getAndSet(0);
            if (blocks == 0) continue;
            flushToDB(entry.getKey().toString(), blocks);
        }
    }

    // ── Intern ────────────────────────────────────────────────────────────

    private void flushToDB(String uuid, long blocks) {
        try (Connection c = con();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO sv_mining_blocks (player_uuid,total_blocks) VALUES(?,?)" +
                 " ON DUPLICATE KEY UPDATE total_blocks = total_blocks + ?")) {
            ps.setString(1, uuid);
            ps.setLong(2, blocks);
            ps.setLong(3, blocks);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Mining] flush: " + e.getMessage());
        }
    }

    // ── Erz-Mapping ───────────────────────────────────────────────────────

    /**
     * Gibt den Erz-Typ für ein Material zurück, oder null wenn kein Erz.
     */
    public static String oreType(Material mat) {
        return switch (mat) {
            case DIAMOND_ORE,    DEEPSLATE_DIAMOND_ORE  -> "diamond";
            case ANCIENT_DEBRIS                          -> "ancient_debris";
            case EMERALD_ORE,    DEEPSLATE_EMERALD_ORE  -> "emerald";
            case GOLD_ORE,       DEEPSLATE_GOLD_ORE,
                 NETHER_GOLD_ORE                         -> "gold";
            case IRON_ORE,       DEEPSLATE_IRON_ORE     -> "iron";
            case COAL_ORE,       DEEPSLATE_COAL_ORE     -> "coal";
            case LAPIS_ORE,      DEEPSLATE_LAPIS_ORE    -> "lapis";
            case REDSTONE_ORE,   DEEPSLATE_REDSTONE_ORE -> "redstone";
            case COPPER_ORE,     DEEPSLATE_COPPER_ORE   -> "copper";
            default -> null;
        };
    }
}
