package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BestiaryGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // ── Mob entry definitions (ordered by level) ──────────────────────────────

    private record MobEntry(String mobType, String displayName, Material icon, String levelRange) {}

    private static final List<MobEntry> MOBS = List.of(
        new MobEntry("ZOMBIE",      "Zombie",        Material.ROTTEN_FLESH,       "Lv 1-9"),
        new MobEntry("SKELETON",    "Skelett",       Material.BONE,               "Lv 10-24"),
        new MobEntry("CAVE_SPIDER", "Höhlenspinne",  Material.SPIDER_EYE,         "Lv 25-49"),
        new MobEntry("RAVAGER",     "Verwüster",     Material.RAVAGER_SPAWN_EGG,  "Lv 50-74 & Lv 200-299"),
        new MobEntry("VINDICATOR",  "Rächer",        Material.IRON_AXE,           "Lv 75-99 & Lv 300-499"),
        new MobEntry("PILLAGER",    "Plünderer",     Material.CROSSBOW,           "Lv 150-199"),
        new MobEntry("EVOKER",      "Beschwörer",    Material.TOTEM_OF_UNDYING,   "Lv 500-749"),
        new MobEntry("IRON_GOLEM",  "Eisengolem",    Material.IRON_BLOCK,         "Lv 500-749"),
        new MobEntry("WARDEN",      "Wächter",       Material.SCULK_SENSOR,       "Lv 750+")
    );

    // 9 mob slots: 3 per row × 3 rows (rows 1, 2, 3 of the 54-slot inventory)
    private static final int[] MOB_SLOTS = {10, 12, 14, 19, 21, 23, 28, 30, 32};

    private final PHSmash plugin;

    public BestiaryGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    // ── Inventory holder ──────────────────────────────────────────────────────

    public static class BestiaryHolder implements InventoryHolder {
        private final UUID playerUuid;
        BestiaryHolder(UUID playerUuid) { this.playerUuid = playerUuid; }
        public UUID getPlayerUuid() { return playerUuid; }
        @Override public Inventory getInventory() { return null; }
    }

    // ── Open / fill ───────────────────────────────────────────────────────────

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(
            new BestiaryHolder(player.getUniqueId()),
            54,
            LEGACY.deserialize("§5§l📖 Bestiarium"));
        fill(inv, player);
        player.openInventory(inv);
    }

    private void fill(Inventory inv, Player player) {
        // ── Gray glass filler ──────────────────────────────────────────────
        ItemStack pane = makePure(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 0; s < 54; s++) inv.setItem(s, pane);

        // ── Row 0: dark border ─────────────────────────────────────────────
        ItemStack darkPane = makePure(Material.BLACK_STAINED_GLASS_PANE);
        for (int s = 0; s < 9; s++) inv.setItem(s, darkPane);

        // ── Slot 4: title item ─────────────────────────────────────────────
        inv.setItem(4, buildTitleItem());

        // ── Mob items ──────────────────────────────────────────────────────
        UUID uuid = player.getUniqueId();
        for (int i = 0; i < MOBS.size(); i++) {
            inv.setItem(MOB_SLOTS[i], buildMobItem(uuid, MOBS.get(i)));
        }

        // ── Close button ───────────────────────────────────────────────────
        inv.setItem(49, buildClose());
    }

    // ── Title item (slot 4) ────────────────────────────────────────────────────

    private ItemStack buildTitleItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§5§l📖 Bestiarium"));
        meta.lore(List.of(
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Alle besiegten Boss-Typen"),
            LEGACY.deserialize("§7Erster Kill: §6+200 Münzen"),
            LEGACY.deserialize("§8─────────────────────")
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ── Mob item ───────────────────────────────────────────────────────────────

    private ItemStack buildMobItem(UUID uuid, MobEntry entry) {
        int kills = plugin.getBestiaryManager().getKills(uuid, entry.mobType());

        ItemStack item = new ItemStack(entry.icon());
        ItemMeta  meta = item.getItemMeta();

        meta.displayName(LEGACY.deserialize("§f" + entry.displayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));

        // Kill count line — gray if 0, green text if > 0
        if (kills == 0) {
            lore.add(LEGACY.deserialize("§7Kills: §70"));
        } else {
            lore.add(LEGACY.deserialize("§7Kills: §a" + kills));
        }

        // Level range
        lore.add(LEGACY.deserialize("§7Boss-Level-Bereich: §f" + entry.levelRange()));

        lore.add(LEGACY.deserialize("§8─────────────────────"));

        // Status line
        if (kills == 0) {
            lore.add(LEGACY.deserialize("§c✗ Noch nicht besiegt"));
        } else if (kills == 1) {
            lore.add(LEGACY.deserialize("§a✔ Erstmals besiegt! §6+200 Münzen erhalten"));
        } else {
            lore.add(LEGACY.deserialize("§a✔ §f" + kills + "x §7besiegt"));
        }

        meta.lore(lore);

        // Enchant glow if at least one kill
        if (kills > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    // ── Close button ───────────────────────────────────────────────────────────

    private ItemStack buildClose() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§cSchließen"));
        item.setItemMeta(meta);
        return item;
    }

    // ── Click handler ──────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BestiaryHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Only close button is interactive; mob items are read-only
        if (slot == 49) { player.closeInventory(); }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ItemStack makePure(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }
}
