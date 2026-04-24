package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verwaltet Ofen-Upgrades.
 *
 * Speicherung: In-Memory-Cache + DB (sv_furnace_upgrades) für Zuverlässigkeit
 * beim Serverneustart. Item-PDC wird beim Abbau auf den Drop geschrieben und
 * beim Platzieren zurück in Cache + DB geladen, sodass Upgrades beim Umsetzen
 * des Ofens erhalten bleiben.
 */
public class FurnaceUpgradeManager {

    public static final int MAX_LEVEL = 6;

    private static final int[] COOK_TICKS = { 0, 200, 150, 100, 60, 30, 21 };
    public  static final long[] COSTS     = { 0, 0, 500, 1_500, 4_000, 10_000, 25_000 };
    public  static final String[] NAMES   = { "", "Normal", "Verbessert", "Schnell", "Blitz", "Quantumfusion", "Plasmaschmelze" };
    public  static final int[] SPEED_PCT  = { 0, 0, 25, 50, 70, 85, 90 };

    private final PHSurvival plugin;
    private final NamespacedKey itemKey;   // PDC-Key für Item-Transfers
    private final Map<String, Integer> cache = new HashMap<>();

    public FurnaceUpgradeManager(PHSurvival plugin) {
        this.plugin  = plugin;
        this.itemKey = new NamespacedKey(plugin, "furnace_level");
        createTable();
        loadAll();
    }

    // ── Koordinaten-Cache + DB ────────────────────────────────────────────

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
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
            plugin.getLogger().warning("[FurnaceUpgrade] Tabelle: " + e.getMessage());
        }
    }

    private void loadAll() {
        try (Connection c = con();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT world,x,y,z,level FROM sv_furnace_upgrades");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cache.put(
                    rs.getString("world") + ";" + rs.getInt("x") + ";"
                    + rs.getInt("y") + ";" + rs.getInt("z"),
                    rs.getInt("level")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[FurnaceUpgrade] Laden: " + e.getMessage());
        }
    }

    private void saveAsync(Block block, int level) {
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
                plugin.getLogger().warning("[FurnaceUpgrade] Speichern: " + e.getMessage());
            }
        });
    }

    private void deleteAsync(Block block) {
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
                plugin.getLogger().warning("[FurnaceUpgrade] Löschen: " + e.getMessage());
            }
        });
    }

    // ── Öffentliche API (Block) ───────────────────────────────────────────

    public int getLevel(Block block) {
        return cache.getOrDefault(key(block), 1);
    }

    public int getCookTicks(Block block) {
        return COOK_TICKS[Math.min(getLevel(block), MAX_LEVEL)];
    }

    public void setLevel(Block block, int level) {
        cache.put(key(block), level);
        saveAsync(block, level);
    }

    /** Entfernt das Upgrade aus Cache + DB (beim Abbau). */
    public void remove(Block block) {
        cache.remove(key(block));
        deleteAsync(block);
    }

    public boolean tryUpgrade(Block block, org.bukkit.entity.Player player) {
        int current = getLevel(block);
        if (current >= MAX_LEVEL) return false;
        int next = current + 1;
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), COSTS[next])) return false;
        setLevel(block, next);
        return true;
    }

    // ── Item-PDC für Transfer beim Umsetzen ──────────────────────────────

    public int getLevelFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;
        return item.getItemMeta()
                   .getPersistentDataContainer()
                   .getOrDefault(itemKey, PersistentDataType.INTEGER, 1);
    }

    public void applyLevelToItem(ItemStack item, int level) {
        if (item == null || level <= 1) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.INTEGER, level);
        meta.displayName(Component.text("⚒ " + friendlyName(item.getType())
            + " (Lv. " + level + " – " + NAMES[level] + ")", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Schmelzgeschwindigkeit: +" + SPEED_PCT[level] + "%", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Shift + Rechtsklick zum Verwalten", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
    }

    private static String friendlyName(Material mat) {
        return switch (mat) {
            case FURNACE       -> "Ofen";
            case BLAST_FURNACE -> "Hochofen";
            case SMOKER        -> "Räucherofen";
            default            -> mat.name();
        };
    }
}
