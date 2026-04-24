package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * Trichter-Upgrade-System – UUID-basiert.
 *
 * DB: sv_hopper_upgrades (furnace_id PK, level, world, x, y, z)
 * Upgrade-Effekt: mehr Items pro Transfer.
 */
public class HopperUpgradeManager {

    public static final int MAX_LEVEL = 6;

    // Items die pro Transfer-Tick bewegt werden
    public static final int[] ITEMS_PER_TRANSFER = { 0, 1, 2, 4, 8, 16, 32 };
    public static final long[] COSTS             = { 0, 0, 500, 1_500, 4_000, 10_000, 25_000 };
    public static final String[] NAMES           = { "", "Normal", "Verbessert", "Schnell", "Blitz", "Quantumflow", "Plasmaflow" };

    private final PHSurvival plugin;
    private final NamespacedKey itemKey;

    private final Map<String, String>  locToId   = new HashMap<>();
    private final Map<String, Integer> idToLevel = new HashMap<>();

    public HopperUpgradeManager(PHSurvival plugin) {
        this.plugin  = plugin;
        this.itemKey = new NamespacedKey(plugin, "hopper_id");
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
                "CREATE TABLE IF NOT EXISTS sv_hopper_upgrades (" +
                "  hopper_id CHAR(36) NOT NULL PRIMARY KEY," +
                "  level TINYINT NOT NULL DEFAULT 1," +
                "  owner_uuid VARCHAR(36)," +
                "  world VARCHAR(64)," +
                "  x INT, y INT, z INT" +
                ")"
            );
            try (ResultSet rs = c.getMetaData().getColumns(null, null, "sv_hopper_upgrades", "owner_uuid")) {
                if (!rs.next()) {
                    s.execute("ALTER TABLE sv_hopper_upgrades ADD COLUMN owner_uuid VARCHAR(36) AFTER level");
                    plugin.getLogger().info("[HopperUpgrade] owner_uuid Spalte hinzugefügt.");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[HopperUpgrade] Tabelle: " + e.getMessage());
        }
    }

    private void loadAll() {
        try (Connection c = con();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT hopper_id, level, world, x, y, z FROM sv_hopper_upgrades");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id    = rs.getString("hopper_id");
                int    level = rs.getInt("level");
                idToLevel.put(id, level);
                String world = rs.getString("world");
                if (world != null) {
                    locToId.put(world + ";" + rs.getInt("x") + ";" + rs.getInt("y") + ";" + rs.getInt("z"), id);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[HopperUpgrade] Laden: " + e.getMessage());
        }
    }

    // ── Öffentliche API ───────────────────────────────────────────────────

    public int getLevel(Block block) {
        String id = locToId.get(coordKey(block));
        return id == null ? 1 : idToLevel.getOrDefault(id, 1);
    }

    public boolean tryUpgrade(Block block, org.bukkit.entity.Player player) {
        int current = getLevel(block);
        if (current >= MAX_LEVEL) return false;
        int next = current + 1;
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), COSTS[next])) return false;
        setLevel(block, next, player.getUniqueId().toString());
        return true;
    }

    public void setLevel(Block block, int level) {
        setLevel(block, level, null);
    }

    private void setLevel(Block block, int level, String ownerUuid) {
        String id = locToId.get(coordKey(block));
        if (id == null) {
            id = UUID.randomUUID().toString();
            locToId.put(coordKey(block), id);
            idToLevel.put(id, level);
            insertOrUpdate(id, level, block, ownerUuid);
        } else {
            idToLevel.put(id, level);
            updateLevelAsync(id, level);
        }
    }

    public String removeAndGetId(Block block) {
        String id = locToId.remove(coordKey(block));
        if (id != null) clearCoordinatesAsync(id);
        return id;
    }

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
            "⚒ Trichter (Lv. " + level + " – " + NAMES[level] + ")",
            NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Transfer: " + ITEMS_PER_TRANSFER[level] + " Items/Tick", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Shift + Rechtsklick zum Verwalten", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
    }

    // ── Async DB ─────────────────────────────────────────────────────────

    private void db(String sql, Object... params) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = con(); PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++)
                    ps.setObject(i + 1, params[i]);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[HopperUpgrade] DB: " + e.getMessage());
            }
        });
    }

    private void insertOrUpdate(String id, int level, Block block, String ownerUuid) {
        db("INSERT INTO sv_hopper_upgrades (hopper_id,level,owner_uuid,world,x,y,z) VALUES(?,?,?,?,?,?,?) " +
           "ON DUPLICATE KEY UPDATE level=VALUES(level),world=VALUES(world),x=VALUES(x),y=VALUES(y),z=VALUES(z)",
           id, level, ownerUuid, block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private void updateLevelAsync(String id, int level) {
        db("UPDATE sv_hopper_upgrades SET level=? WHERE hopper_id=?", level, id);
    }

    private void clearCoordinatesAsync(String id) {
        db("UPDATE sv_hopper_upgrades SET world=NULL,x=NULL,y=NULL,z=NULL WHERE hopper_id=?", id);
    }

    private void updateCoordinatesAsync(String id, Block block) {
        db("UPDATE sv_hopper_upgrades SET world=?,x=?,y=?,z=? WHERE hopper_id=?",
           block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), id);
    }
}
