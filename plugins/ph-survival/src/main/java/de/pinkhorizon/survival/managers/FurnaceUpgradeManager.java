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

    public static final int MAX_LEVEL = 10;

    // Lv1=10%, Lv10=200% (linear: +~21.1% pro Stufe)
    // CookTicks = 200 / (1 + speed/100), gerundet
    private static final int[] COOK_TICKS = { 0, 182, 153, 132, 116, 103, 93, 85, 78, 72, 67 };
    public  static final long[] COSTS     = { 0, 0, 1_000, 2_500, 5_000, 10_000, 20_000, 40_000, 70_000, 110_000, 150_000 };
    public  static final String[] NAMES   = { "", "Normal", "Verbessert", "Schnell", "Präzise", "Blitz", "Turbo", "Ultra", "Hyper", "Quantum", "Plasma" };
    public  static final int[] SPEED_PCT  = { 0, 10, 31, 52, 73, 94, 115, 136, 157, 178, 200 };

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
            // Migration: Tabelle ohne furnace_id oder ohne owner_uuid droppen
            try (ResultSet rs = c.getMetaData().getColumns(null, null, "sv_furnace_upgrades", "furnace_id")) {
                if (!rs.next()) {
                    s.execute("DROP TABLE IF EXISTS sv_furnace_upgrades");
                    plugin.getLogger().info("[FurnaceUpgrade] Alte Tabelle entfernt (Schema-Migration).");
                }
            }
            s.execute(
                "CREATE TABLE IF NOT EXISTS sv_furnace_upgrades (" +
                "  furnace_id CHAR(36) NOT NULL PRIMARY KEY," +
                "  level TINYINT NOT NULL DEFAULT 1," +
                "  owner_uuid VARCHAR(36)," +
                "  world VARCHAR(64)," +
                "  x INT, y INT, z INT" +
                ")"
            );
            // owner_uuid nachträglich hinzufügen falls Tabelle ohne diese Spalte existiert
            try (ResultSet rs = c.getMetaData().getColumns(null, null, "sv_furnace_upgrades", "owner_uuid")) {
                if (!rs.next()) {
                    s.execute("ALTER TABLE sv_furnace_upgrades ADD COLUMN owner_uuid VARCHAR(36) AFTER level");
                    plugin.getLogger().info("[FurnaceUpgrade] owner_uuid Spalte hinzugefügt.");
                }
            }
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

    /** Entfernt verwaiste Einträge – Öfen die in der DB stehen aber nicht mehr in der Welt existieren. */
    public void cleanupOrphaned() {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            java.util.List<String[]> toCheck = new java.util.ArrayList<>();
            try (Connection c = con();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT furnace_id, world, x, y, z FROM sv_furnace_upgrades WHERE world IS NOT NULL");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    toCheck.add(new String[]{
                        rs.getString("furnace_id"), rs.getString("world"),
                        String.valueOf(rs.getInt("x")), String.valueOf(rs.getInt("y")), String.valueOf(rs.getInt("z"))
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[FurnaceUpgrade] Cleanup-Query: " + e.getMessage());
                return;
            }

            int removed = 0;
            for (String[] row : toCheck) {
                String id = row[0]; String worldName = row[1];
                int x = Integer.parseInt(row[2]), y = Integer.parseInt(row[3]), z = Integer.parseInt(row[4]);
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world == null) continue;
                org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                boolean isFurnace = block.getType() == org.bukkit.Material.FURNACE
                    || block.getType() == org.bukkit.Material.BLAST_FURNACE
                    || block.getType() == org.bukkit.Material.SMOKER;
                if (!isFurnace) {
                    String key = worldName + ";" + x + ";" + y + ";" + z;
                    locToId.remove(key);
                    db("UPDATE sv_furnace_upgrades SET world=NULL,x=NULL,y=NULL,z=NULL WHERE furnace_id=?", id);
                    removed++;
                }
            }
            if (removed > 0)
                plugin.getLogger().info("[FurnaceUpgrade] " + removed + " verwaiste Einträge bereinigt.");
        }, 40L); // 2s nach Start, Welten sind dann geladen
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

    /** Setzt owner_uuid falls noch nicht gesetzt (NULL) – kostenlos, beim GUI-Öffnen. */
    public void claimOwnership(Block block, String ownerUuid) {
        String id = locToId.get(coordKey(block));
        if (id == null || ownerUuid == null) return;
        db("UPDATE sv_furnace_upgrades SET owner_uuid=? WHERE furnace_id=? AND owner_uuid IS NULL", ownerUuid, id);
    }

    public boolean tryUpgrade(Block block, org.bukkit.entity.Player player) {
        int current = getLevel(block);
        if (current >= MAX_LEVEL) return false;
        int next = current + 1;
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), COSTS[next])) return false;
        setLevel(block, next, player.getUniqueId().toString());
        return true;
    }

    /** Setzt Level – generiert beim ersten Mal eine neue UUID. */
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
            if (ownerUuid != null) {
                db("UPDATE sv_furnace_upgrades SET level=?, owner_uuid=COALESCE(owner_uuid,?) WHERE furnace_id=?", level, ownerUuid, id);
            } else {
                updateLevelAsync(id, level);
            }
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

    private void db(String sql, Object... params) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = con(); PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++)
                    ps.setObject(i + 1, params[i]);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[FurnaceUpgrade] DB: " + e.getMessage());
            }
        });
    }

    private void insertOrUpdate(String id, int level, Block block, String ownerUuid) {
        db("INSERT INTO sv_furnace_upgrades (furnace_id,level,owner_uuid,world,x,y,z) VALUES(?,?,?,?,?,?,?) " +
           "ON DUPLICATE KEY UPDATE level=VALUES(level),world=VALUES(world),x=VALUES(x),y=VALUES(y),z=VALUES(z)",
           id, level, ownerUuid, block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private void updateLevelAsync(String id, int level) {
        db("UPDATE sv_furnace_upgrades SET level=? WHERE furnace_id=?", level, id);
    }

    private void clearCoordinatesAsync(String id) {
        db("UPDATE sv_furnace_upgrades SET world=NULL,x=NULL,y=NULL,z=NULL WHERE furnace_id=?", id);
    }

    private void updateCoordinatesAsync(String id, Block block) {
        db("UPDATE sv_furnace_upgrades SET world=?,x=?,y=?,z=? WHERE furnace_id=?",
           block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), id);
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
