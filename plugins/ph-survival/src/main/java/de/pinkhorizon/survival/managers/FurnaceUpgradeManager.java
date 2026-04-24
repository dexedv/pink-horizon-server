package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.block.Block;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Verwaltet Ofen-Upgrades: Level pro Block-Koordinate.
 * Daten werden in sv_furnace_upgrades (ph_survival) gespeichert.
 *
 * Bedienung: Shift + Rechtsklick auf Ofen / Schmelzofen / Hochofen → GUI öffnet sich.
 */
public class FurnaceUpgradeManager {

    public static final int MAX_LEVEL = 5;

    // Schmelz-Ticks pro Level (200 = Standard = 10 Sekunden)
    private static final int[] COOK_TICKS  = { 0, 200, 150, 100, 60, 30 };
    // Upgrade-Kosten in Coins (Index = Ziel-Level)
    public  static final long[] COSTS      = { 0, 0, 500, 1_500, 4_000, 10_000 };
    // Anzeigenamen
    public  static final String[] NAMES    = { "", "Normal", "Verbessert", "Schnell", "Blitz", "Quantumfusion" };
    // Prozentuale Beschleunigung gegenüber Level 1
    public  static final int[] SPEED_PCT   = { 0, 0, 25, 50, 70, 85 };

    private final PHSurvival plugin;
    private final Map<String, Integer> cache = new HashMap<>();

    public FurnaceUpgradeManager(PHSurvival plugin) {
        this.plugin = plugin;
        createTable();
        loadAll();
    }

    // ── Internes ──────────────────────────────────────────────────────────

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void createTable() {
        try (Connection c = con(); Statement s = c.createStatement()) {
            s.execute(
                "CREATE TABLE IF NOT EXISTS sv_furnace_upgrades (" +
                "  world VARCHAR(64) NOT NULL," +
                "  x INT NOT NULL, y INT NOT NULL, z INT NOT NULL," +
                "  level TINYINT NOT NULL DEFAULT 1," +
                "  PRIMARY KEY (world, x, y, z)" +
                ")"
            );
        } catch (SQLException e) {
            plugin.getLogger().warning("[FurnaceUpgrade] Tabelle konnte nicht erstellt werden: " + e.getMessage());
        }
    }

    private void loadAll() {
        try (Connection c = con();
             PreparedStatement ps = c.prepareStatement("SELECT world,x,y,z,level FROM sv_furnace_upgrades");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String k = rs.getString("world") + ";"
                         + rs.getInt("x") + ";"
                         + rs.getInt("y") + ";"
                         + rs.getInt("z");
                cache.put(k, rs.getInt("level"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[FurnaceUpgrade] Laden fehlgeschlagen: " + e.getMessage());
        }
    }

    // ── Öffentliche API ───────────────────────────────────────────────────

    public int getLevel(Block block) {
        return cache.getOrDefault(key(block), 1);
    }

    public int getCookTicks(Block block) {
        int lvl = getLevel(block);
        return COOK_TICKS[Math.min(lvl, MAX_LEVEL)];
    }

    /** Gibt true zurück und zieht Coins ab, wenn das Upgrade möglich ist. */
    public boolean tryUpgrade(Block block, org.bukkit.entity.Player player) {
        int current = getLevel(block);
        if (current >= MAX_LEVEL) return false;
        int next = current + 1;
        long cost = COSTS[next];
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), cost)) return false;
        setLevel(block, next);
        return true;
    }

    public void setLevel(Block block, int level) {
        String k = key(block);
        cache.put(k, level);
        final String world = block.getWorld().getName();
        final int bx = block.getX(), by = block.getY(), bz = block.getZ();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = con();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sv_furnace_upgrades (world,x,y,z,level) VALUES(?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE level=?")) {
                ps.setString(1, world);
                ps.setInt(2, bx); ps.setInt(3, by); ps.setInt(4, bz);
                ps.setInt(5, level); ps.setInt(6, level);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[FurnaceUpgrade] Speichern fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    public void remove(Block block) {
        String k = key(block);
        if (!cache.containsKey(k)) return;
        cache.remove(k);
        final String world = block.getWorld().getName();
        final int bx = block.getX(), by = block.getY(), bz = block.getZ();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = con();
                 PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM sv_furnace_upgrades WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, world);
                ps.setInt(2, bx); ps.setInt(3, by); ps.setInt(4, bz);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[FurnaceUpgrade] Löschen fehlgeschlagen: " + e.getMessage());
            }
        });
    }
}
