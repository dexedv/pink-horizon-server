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
import java.util.UUID;

/**
 * Ofen-Upgrade-System – UUID-basiert.
 *
 * Jeder upgegradete Ofen bekommt eine eindeutige UUID.
 * DB: sv_furnace_upgrades (furnace_id PK, level, world, x, y, z)
 *
 * Koordinaten sind NULL wenn der Ofen sich im Inventar befindet.
 * Item-PDC trägt die UUID → Upgrade bleibt beim Umsetzen erhalten.
 */
public class FurnaceUpgradeManager {

    public static final int MAX_LEVEL = 6;

    private static final int[] COOK_TICKS = { 0, 200, 150, 100, 60, 30, 21 };
    public  static final long[] COSTS     = { 0, 0, 500, 1_500, 4_000, 10_000, 25_000 };
    public  static final String[] NAMES   = { "", "Normal", "Verbessert", "Schnell", "Blitz", "Quantumfusion", "Plasmaschmelze" };
    public  static final int[] SPEED_PCT  = { 0, 0, 25, 50, 70, 85, 90 };

    private final PHSurvival plugin;
    private final NamespacedKey itemKey;  // UUID auf Item-PDC

    // coordinate key ("world;x;y;z") → furnace UUID
    private final Map<String, String> locToId    = new HashMap<>();
    // furnace UUID → level
    private final Map<String, Integer> idToLevel = new HashMap<>();

    public FurnaceUpgradeManager(PHSurvival plugin) {
        this.plugin  = plugin;
        this.itemKey = new NamespacedKey(plugin, "furnace_id");
        createTable();
        loadAll();
    }

    // ── DB ────────────────────────────────────────────────────────────────

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    private static String coordKey(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void createTable() {
        try (Connection c = con(); Statement s = c.createStatement()) {
            s.execute(
                "CREATE TABLE IF NOT EXISTS sv_furnace_upgrades (" +
                "  furnace_id CHAR(36) NOT NULL PRIMARY KEY," +
                "  level TINYINT NOT NULL DEFAULT 1," +
                "  world VARCHAR(64)," +
                "  x INT, y INT, z INT" +
                ")"
            );
        } catch (SQLException e) {
            plugin.getLogger().warning("[FurnaceUpgrade] Tabelle: " + e.getMessage());
        }
    }

    private void loadAll() {
        try (Connection c = con();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT furnace_id, level, world, x, y, z FROM sv_furnace_upgrades");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id    = rs.getString("furnace_id");
                int    level = rs.getInt("level");
                idToLevel.put(id, level);
                String world = rs.getString("world");
                if (world != null) {
                    locToId.put(world + ";" + rs.getInt("x") + ";" + rs.getInt("y") + ";" + rs.getInt("z"), id);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[FurnaceUpgrade] Laden: " + e.getMessage());
        }
    }

    // ── Öffentliche API ───────────────────────────────────────────────────

    public int getLevel(Block block) {
        String id = locToId.get(coordKey(block));
        if (id == null) return 1;
        return idToLevel.getOrDefault(id, 1);
    }

    public int getCookTicks(Block block) {
        return COOK_TICKS[Math.min(getLevel(block), MAX_LEVEL)];
    }

    public boolean tryUpgrade(Block block, org.bukkit.entity.Player player) {
        int current = getLevel(block);
        if (current >= MAX_LEVEL) return false;
        int next = current + 1;
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), COSTS[next])) return false;
        setLevel(block, next);
        return true;
    }

    /** Setzt Level – generiert beim ersten Mal eine neue UUID. */
    public void setLevel(Block block, int level) {
        String id = locToId.get(coordKey(block));
        if (id == null) {
            id = UUID.randomUUID().toString();
            locToId.put(coordKey(block), id);
            idToLevel.put(id, level);
            insertAsync(id, level, block);
        } else {
            idToLevel.put(id, level);
            updateLevelAsync(id, level);
        }
    }

    /** Ofen abgebaut: aus Koordinaten-Cache entfernen, DB-Koordinaten auf NULL. */
    public String removeAndGetId(Block block) {
        String id = locToId.remove(coordKey(block));
        if (id != null) clearCoordinatesAsync(id);
        return id;
    }

    /** Ofen platziert: UUID in Koordinaten-Cache + DB-Koordinaten aktualisieren. */
    public void placeWithId(Block block, String id) {
        locToId.put(coordKey(block), id);
        updateCoordinatesAsync(id, block);
    }

    // ── Item-PDC ─────────────────────────────────────────────────────────

    public String getIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                   .get(itemKey, PersistentDataType.STRING);
    }

    public int getLevelForId(String id) {
        return id == null ? 1 : idToLevel.getOrDefault(id, 1);
    }

    public void applyToItem(ItemStack item, String id) {
        if (item == null || id == null) return;
        int level = getLevelForId(id);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.displayName(Component.text(
            "⚒ " + friendlyName(item.getType()) + " (Lv. " + level + " – " + NAMES[level] + ")",
            NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Schmelzgeschwindigkeit: +" + SPEED_PCT[level] + "%", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Shift + Rechtsklick zum Verwalten", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
    }

    // ── Async DB-Operationen ──────────────────────────────────────────────

    private void insertAsync(String id, int level, Block block) {
        final String world = block.getWorld().getName();
        final int bx = block.getX(), by = block.getY(), bz = block.getZ();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = con();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sv_furnace_upgrades (furnace_id,level,world,x,y,z) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1, id); ps.setInt(2, level);
                ps.setString(3, world); ps.setInt(4, bx); ps.setInt(5, by); ps.setInt(6, bz);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[FurnaceUpgrade] Insert: " + e.getMessage());
            }
        });
    }

    private void updateLevelAsync(String id, int level) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = con();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE sv_furnace_upgrades SET level=? WHERE furnace_id=?")) {
                ps.setInt(1, level); ps.setString(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[FurnaceUpgrade] UpdateLevel: " + e.getMessage());
            }
        });
    }

    private void clearCoordinatesAsync(String id) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = con();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE sv_furnace_upgrades SET world=NULL,x=NULL,y=NULL,z=NULL WHERE furnace_id=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[FurnaceUpgrade] ClearCoords: " + e.getMessage());
            }
        });
    }

    private void updateCoordinatesAsync(String id, Block block) {
        final String world = block.getWorld().getName();
        final int bx = block.getX(), by = block.getY(), bz = block.getZ();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = con();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE sv_furnace_upgrades SET world=?,x=?,y=?,z=? WHERE furnace_id=?")) {
                ps.setString(1, world); ps.setInt(2, bx); ps.setInt(3, by); ps.setInt(4, bz);
                ps.setString(5, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[FurnaceUpgrade] UpdateCoords: " + e.getMessage());
            }
        });
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
