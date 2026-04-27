package de.pinkhorizon.runes.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.runes.PHRunes;
import de.pinkhorizon.runes.RuneType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.*;

/**
 * Verwaltet Runen-Gravuren auf Items.
 * Runen werden als PDC-Tag gespeichert: "runen" → komma-getrennte RuneType-Namen
 * und "runen_slots" → aktuelle Slot-Nutzung.
 */
public class RuneManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHRunes plugin;
    private final HikariDataSource ds;
    private final NamespacedKey RUNES_KEY;
    private final NamespacedKey SLOTS_KEY;
    private final NamespacedKey RUNE_ITEM_KEY;

    public RuneManager(PHRunes plugin) {
        this.plugin        = plugin;
        this.RUNES_KEY     = new NamespacedKey(plugin, "runen");
        this.SLOTS_KEY     = new NamespacedKey(plugin, "runen_slots");
        this.RUNE_ITEM_KEY = new NamespacedKey(plugin, "rune_type");
        this.ds            = initPool();
        createTable();
    }

    // ── DB ───────────────────────────────────────────────────────────────────

    private HikariDataSource initPool() {
        var cfg = plugin.getConfig().getConfigurationSection("database");
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://"
            + cfg.getString("host", "ph-mysql") + ":" + cfg.getInt("port", 3306)
            + "/" + cfg.getString("name", "ph_skyblock") + "?useSSL=false&serverTimezone=UTC");
        hc.setUsername(cfg.getString("user", "ph_user"));
        hc.setPassword(cfg.getString("password", ""));
        hc.setMaximumPoolSize(cfg.getInt("pool-size", 4));
        hc.setPoolName("PHRunes-Pool");
        return new HikariDataSource(hc);
    }

    private void createTable() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_player_runes (
                    player_uuid  VARCHAR(36) NOT NULL,
                    rune_type    VARCHAR(64) NOT NULL,
                    amount       INT          DEFAULT 1,
                    PRIMARY KEY (player_uuid, rune_type)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Runes-Tabelle Fehler: " + e.getMessage());
        }
    }

    // ── Gravur ───────────────────────────────────────────────────────────────

    /**
     * Graviert eine Rune in das Item in der Haupthand des Spielers.
     * @return true wenn erfolgreich
     */
    public boolean engraveRune(Player player, RuneType rune) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage(MM.deserialize("<red>Halte das Item in der Hand, das du gravieren möchtest!"));
            return false;
        }

        String category = detectCategory(item.getType());
        if (category == null) {
            player.sendMessage(MM.deserialize("<red>Dieses Item kann nicht mit Runen graviert werden."));
            return false;
        }

        if (!rune.isCompatibleWith(category)) {
            player.sendMessage(MM.deserialize("<red>" + rune.displayName + " kann nicht auf " + category + " graviert werden!"));
            return false;
        }

        // Spieler hat die Rune?
        if (!playerHasRune(player.getUniqueId(), rune)) {
            player.sendMessage(MM.deserialize("<red>Du besitzt diese Rune nicht! Finde sie beim Void-Fishing oder in Dungeons."));
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        List<RuneType> existing = getRunesFromItem(item);
        int usedSlots = existing.stream().mapToInt(r -> r.slotCost).sum();
        int maxSlots  = RuneType.maxSlotsFor(category);

        if (usedSlots + rune.slotCost > maxSlots) {
            player.sendMessage(MM.deserialize("<red>Nicht genug Runen-Slots! Benötigt: " + rune.slotCost
                + ", Verfügbar: " + (maxSlots - usedSlots)));
            return false;
        }

        if (existing.contains(rune)) {
            player.sendMessage(MM.deserialize("<red>Diese Rune ist bereits graviert!"));
            return false;
        }

        // Rune hinzufügen
        existing.add(rune);
        String runeString = String.join(",", existing.stream().map(Enum::name).toList());
        meta.getPersistentDataContainer().set(RUNES_KEY, PersistentDataType.STRING, runeString);
        meta.getPersistentDataContainer().set(SLOTS_KEY, PersistentDataType.INTEGER, usedSlots + rune.slotCost);

        // Lore aktualisieren
        updateItemLore(item, meta, existing, usedSlots + rune.slotCost, maxSlots);
        item.setItemMeta(meta);

        // Rune aus Inventar entfernen
        consumeRune(player.getUniqueId(), rune);

        player.sendMessage(MM.deserialize(rune.colorPrefix + rune.displayName
            + " <green>erfolgreich graviert! <gray>(" + (usedSlots + rune.slotCost) + "/" + maxSlots + " Slots)"));
        return true;
    }

    // ── Item-Analyse ──────────────────────────────────────────────────────────

    public List<RuneType> getRunesFromItem(ItemStack item) {
        if (!item.hasItemMeta()) return new ArrayList<>();
        String raw = item.getItemMeta().getPersistentDataContainer()
            .get(RUNES_KEY, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        List<RuneType> result = new ArrayList<>();
        for (String name : raw.split(",")) {
            try { result.add(RuneType.valueOf(name)); } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public boolean hasRune(ItemStack item, RuneType rune) {
        return getRunesFromItem(item).contains(rune);
    }

    public void showRuneInventory(Player player) {
        new de.pinkhorizon.runes.gui.RuneInventoryGui(plugin, player).open(player);
    }

    @Deprecated
    public void inspectItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage(MM.deserialize("<red>Halte ein Item in der Hand!"));
            return;
        }

        String category = detectCategory(item.getType());
        List<RuneType> runes = getRunesFromItem(item);
        int usedSlots = runes.stream().mapToInt(r -> r.slotCost).sum();
        int maxSlots  = category != null ? RuneType.maxSlotsFor(category) : 0;

        player.sendMessage(MM.deserialize("<gold>══ Runen-Inspektion ══"));
        player.sendMessage(MM.deserialize("<gray>Item: <white>" + item.getType().name()));
        player.sendMessage(MM.deserialize("<gray>Kategorie: <white>" + (category != null ? category : "Nicht gravierbar")));
        player.sendMessage(MM.deserialize("<gray>Slots: <white>" + usedSlots + "/" + maxSlots));

        if (runes.isEmpty()) {
            player.sendMessage(MM.deserialize("<dark_gray>Keine Runen graviert."));
        } else {
            player.sendMessage(MM.deserialize("<gray>Gravierte Runen:"));
            for (RuneType r : runes) {
                player.sendMessage(MM.deserialize("  " + r.colorPrefix + r.displayName
                    + " <dark_gray>(" + r.slotCost + " Slots) <gray>– " + r.description));
            }
        }
    }

    // ── Runen-Item-Erstellung (für Drops/Belohnungen) ─────────────────────────

    public ItemStack createRuneItem(RuneType rune) {
        ItemStack item = new ItemStack(rune.guiIcon);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(MM.deserialize(rune.colorPrefix + "<bold>" + rune.displayName));
        meta.lore(List.of(
            MM.deserialize("<gray>" + rune.description),
            MM.deserialize("<dark_gray>Slots: <white>" + rune.slotCost
                + " <dark_gray>| Erlaubt auf: <white>" + rune.allowedOn),
            MM.deserialize("<yellow>Benutze /rune engrave um zu gravieren")
        ));
        meta.getPersistentDataContainer().set(RUNE_ITEM_KEY, PersistentDataType.STRING, rune.name());
        item.setItemMeta(meta);
        return item;
    }

    public RuneType getRuneTypeFromItem(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
            .get(RUNE_ITEM_KEY, PersistentDataType.STRING);
        if (val == null) return null;
        try { return RuneType.valueOf(val); } catch (IllegalArgumentException e) { return null; }
    }

    // ── Spieler-Runen-Inventar ────────────────────────────────────────────────

    public boolean playerHasRune(UUID uuid, RuneType rune) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT amount FROM sky_player_runes WHERE player_uuid=? AND rune_type=? AND amount > 0")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rune.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public void giveRune(UUID uuid, RuneType rune, int amount) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO sky_player_runes (player_uuid, rune_type, amount) VALUES(?,?,?) "
               + "ON DUPLICATE KEY UPDATE amount = amount + ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rune.name());
            ps.setInt(3, amount);
            ps.setInt(4, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Give rune fehler: " + e.getMessage());
        }
    }

    private void consumeRune(UUID uuid, RuneType rune) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE sky_player_runes SET amount = amount - 1 WHERE player_uuid=? AND rune_type=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rune.name());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public Map<RuneType, Integer> getPlayerRunes(UUID uuid) {
        Map<RuneType, Integer> result = new LinkedHashMap<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT rune_type, amount FROM sky_player_runes WHERE player_uuid=? AND amount > 0")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        RuneType type = RuneType.valueOf(rs.getString("rune_type"));
                        result.put(type, rs.getInt("amount"));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException ignored) {}
        return result;
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private String detectCategory(Material mat) {
        String name = mat.name();
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE") || name.endsWith("_SWORD")) return "TOOL";
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) return "ARMOR";
        if (mat == Material.FISHING_ROD) return "ROD";
        return null;
    }

    private void updateItemLore(ItemStack item, ItemMeta meta, List<RuneType> runes, int usedSlots, int maxSlots) {
        List<Component> lore = new ArrayList<>();

        // Bestehende Lore beibehalten (ohne Runen-Zeilen)
        if (meta.lore() != null) {
            for (Component line : meta.lore()) {
                // Rune-Zeilen überspringen (vereinfacht: Zeilen die mit ◆ beginnen)
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                if (!plain.startsWith("◆") && !plain.startsWith("Slots:")) {
                    lore.add(line);
                }
            }
        }

        // Runen-Slots hinzufügen
        lore.add(MM.deserialize("<dark_gray>Runen-Slots: <white>" + usedSlots + "/" + maxSlots));
        for (RuneType r : runes) {
            lore.add(MM.deserialize("  <dark_gray>◆ " + r.colorPrefix + r.displayName));
        }

        meta.lore(lore);
    }

    public void close() {
        if (ds != null && !ds.isClosed()) ds.close();
    }
}
