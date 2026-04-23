package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import de.pinkhorizon.smash.managers.TalentManager.TalentType;
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

public class TalentGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Talent item slots (one per row, left column) — same pattern as UpgradeGui
    private static final int[] TALENT_SLOTS = {10, 19, 28, 37, 46};
    // Status pane slots (right next to each talent item)
    private static final int[] STATUS_SLOTS = {11, 20, 29, 38, 47};

    private final PHSmash plugin;

    public TalentGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    // ── Inventory holder ──────────────────────────────────────────────────

    public static class TalentHolder implements InventoryHolder {
        private final UUID playerUuid;
        TalentHolder(UUID playerUuid) { this.playerUuid = playerUuid; }
        public UUID getPlayerUuid() { return playerUuid; }
        @Override public Inventory getInventory() { return null; }
    }

    // ── Open / fill ───────────────────────────────────────────────────────

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(
            new TalentHolder(player.getUniqueId()),
            54,
            LEGACY.deserialize("§5§l✦ Talente §8– §7Boss-Kerne ausgeben"));
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
        inv.setItem(4, buildTitleItem(player));

        // ── Talent items + status panes ────────────────────────────────────
        TalentType[] types = TalentType.values();
        for (int i = 0; i < types.length; i++) {
            inv.setItem(TALENT_SLOTS[i], buildTalentItem(player, types[i]));
            inv.setItem(STATUS_SLOTS[i], buildStatusPane(player, types[i]));
        }

        // ── Boss Core resource info ────────────────────────────────────────
        int coreCount = plugin.getLootManager().getQuantity(player.getUniqueId(), LootItem.BOSS_CORE);
        inv.setItem(15, buildCoreInfo(coreCount));

        // ── Close button ───────────────────────────────────────────────────
        inv.setItem(49, buildClose());
    }

    // ── Title item (slot 4) ────────────────────────────────────────────────

    private ItemStack buildTitleItem(Player player) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§5§l✦ Talent-System"));

        TalentType[] types = TalentType.values();
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize("§7Übersicht:"));
        for (TalentType t : types) {
            int level = plugin.getTalentManager().getLevel(player.getUniqueId(), t);
            String tag = level >= t.maxLevel ? "§a✔ MAX" : "§7Lv §f" + level + "§8/§f" + t.maxLevel;
            lore.add(LEGACY.deserialize("  " + t.displayName + " §8– " + tag));
        }
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize("§7Kosten: §5Boss-Kerne"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Talent item ───────────────────────────────────────────────────────

    private ItemStack buildTalentItem(Player player, TalentType type) {
        UUID uuid     = player.getUniqueId();
        int  curLevel = plugin.getTalentManager().getLevel(uuid, type);
        int  nextLv   = curLevel + 1;
        boolean maxed = curLevel >= type.maxLevel;
        int  cost     = type.nextCost(curLevel);
        int  cores    = plugin.getLootManager().getQuantity(uuid, LootItem.BOSS_CORE);
        boolean canAfford = !maxed && cores >= cost;

        Material mat = switch (type) {
            case TREASURE_HUNTER -> Material.EMERALD;
            case IRON_HEART      -> Material.GOLDEN_APPLE;
            case FAST_HANDS      -> Material.FEATHER;
            case COIN_MASTER     -> Material.GOLD_INGOT;
            case BOSS_SLAYER     -> Material.NETHERITE_SWORD;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        String nameColor = maxed ? "§a" : "§f";
        meta.displayName(LEGACY.deserialize(nameColor + type.displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize("§7Level: " + bar(curLevel, type.maxLevel)));
        lore.add(LEGACY.deserialize("§8─────────────────────"));

        if (!maxed) {
            lore.add(LEGACY.deserialize("§7Jetzt:  " + effectText(type, curLevel)));
            lore.add(LEGACY.deserialize("§7→ Lv" + nextLv + ": " + effectText(type, nextLv)));
            lore.add(LEGACY.deserialize("§8─────────────────────"));
            String coreCol = cores >= cost ? "§a" : "§c";
            lore.add(LEGACY.deserialize("§7Kosten: §5" + cost + " §7Boss-Kern(e)"));
            lore.add(LEGACY.deserialize("§7Besitzt: " + coreCol + cores + " Boss-Kerne"));
            lore.add(LEGACY.deserialize("§8─────────────────────"));
            if (canAfford) {
                lore.add(LEGACY.deserialize("§a▶ Klicken!"));
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(LEGACY.deserialize("§c✗ Nicht genug Boss-Kerne"));
            }
        } else {
            lore.add(LEGACY.deserialize("§7Jetzt:  " + effectText(type, curLevel)));
            lore.add(LEGACY.deserialize("§8─────────────────────"));
            lore.add(LEGACY.deserialize("§a✔ MAX"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Status pane (slot next to talent) ─────────────────────────────────

    private ItemStack buildStatusPane(Player player, TalentType type) {
        UUID uuid     = player.getUniqueId();
        int  curLevel = plugin.getTalentManager().getLevel(uuid, type);
        boolean maxed = curLevel >= type.maxLevel;

        if (maxed) {
            ItemStack pane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
            ItemMeta  meta = pane.getItemMeta();
            meta.displayName(LEGACY.deserialize("§5§lMAX"));
            pane.setItemMeta(meta);
            return pane;
        }

        int  cost     = type.nextCost(curLevel);
        int  cores    = plugin.getLootManager().getQuantity(uuid, LootItem.BOSS_CORE);
        boolean canAfford = cores >= cost;

        Material mat  = canAfford ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String   name = canAfford ? "§a§l▶ Kaufbar" : "§c§l✗ Zu teuer";
        ItemStack pane = new ItemStack(mat);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        if (!canAfford) {
            meta.lore(List.of(LEGACY.deserialize(
                "§7Fehlt: §c" + (cost - cores) + " §7Boss-Kern(e)")));
        }
        pane.setItemMeta(meta);
        return pane;
    }

    // ── Boss Core resource info ────────────────────────────────────────────

    private ItemStack buildCoreInfo(int count) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§5§lBoss-Kerne"));
        meta.lore(List.of(
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Vorrat: §5" + count),
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Durch Bosse besiegen verdient")
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ── Close button ───────────────────────────────────────────────────────

    private ItemStack buildClose() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§cSchließen"));
        item.setItemMeta(meta);
        return item;
    }

    // ── Click handler ──────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TalentHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) { player.closeInventory(); return; }

        TalentType[] types = TalentType.values();
        for (int i = 0; i < TALENT_SLOTS.length; i++) {
            if (slot == TALENT_SLOTS[i]) {
                TalentType type = types[i];
                plugin.getTalentManager().tryUpgrade(player, type);
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 1L);
                return;
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ItemStack makePure(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static String effectText(TalentType type, int level) {
        return switch (type) {
            case TREASURE_HUNTER -> "§a+" + (level * 8) + "% §7Loot-Chance";
            case IRON_HEART      -> "§e+" + (level * 8) + " §7Max-HP";
            case FAST_HANDS      -> "§b+" + (level * 5) + "% §7Angriffsgeschw.";
            case COIN_MASTER     -> "§6+" + (level * 10) + "% §7Münzen";
            case BOSS_SLAYER     -> "§c+" + (level * 3) + "% §7Schaden (Boss > Lv50)";
        };
    }

    private static String bar(int current, int max) {
        int len    = 15;
        int filled = max > 0 ? Math.min(len, (int) Math.round(len * (double) current / max)) : 0;
        String col = filled >= len ? "§a" : filled >= len / 2 ? "§e" : "§c";
        return col + "█".repeat(filled) + "§8" + "█".repeat(len - filled)
            + " §7(" + current + "/" + max + ")";
    }
}
