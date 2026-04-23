package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import de.pinkhorizon.smash.managers.UpgradeManager.UpgradeType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UpgradeGui implements Listener, InventoryHolder {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Upgrade item slots (one per row, left column)
    private static final int[] UPGRADE_SLOTS = {10, 19, 28, 37, 46};
    // Status pane slots (right next to each upgrade item)
    private static final int[] STATUS_SLOTS  = {11, 20, 29, 38, 47};

    private final PHSmash   plugin;
    private final Set<UUID> upgrading = new HashSet<>();

    public UpgradeGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() { return null; }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 54,
            LEGACY.deserialize("§c§lUpgrades – Smash the Boss"));
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

        // ── Upgrade items + status panes ───────────────────────────────────
        UpgradeType[] types = UpgradeType.values();
        for (int i = 0; i < types.length; i++) {
            inv.setItem(UPGRADE_SLOTS[i], buildUpgradeItem(player, types[i]));
            inv.setItem(STATUS_SLOTS[i],  buildStatusPane(player, types[i]));
        }

        // ── Resource info items ────────────────────────────────────────────
        inv.setItem(15, buildItemInfo(player, LootItem.IRON_FRAGMENT,  Material.IRON_INGOT));
        inv.setItem(24, buildItemInfo(player, LootItem.GOLD_FRAGMENT,  Material.GOLD_INGOT));
        inv.setItem(33, buildItemInfo(player, LootItem.DIAMOND_SHARD,  Material.DIAMOND));
        inv.setItem(42, buildItemInfo(player, LootItem.BOSS_CORE,      Material.NETHER_STAR));

        // ── Close button ───────────────────────────────────────────────────
        inv.setItem(49, buildClose());
    }

    // ── Title item (slot 4) ────────────────────────────────────────────────

    private ItemStack buildTitleItem(Player player) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§c§l⚔ Upgrade-System §8– §7Materialien ausgeben"));

        UpgradeType[] types = UpgradeType.values();
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize("§7Upgrade-Übersicht:"));
        for (UpgradeType t : types) {
            int level = plugin.getUpgradeManager().getLevel(player.getUniqueId(), t);
            String tag = level >= t.maxLevel ? "§a✔ MAX" : "§7Lv §f" + level + "§8/§f" + t.maxLevel;
            lore.add(LEGACY.deserialize("  " + t.displayName + " §8– " + tag));
        }
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Upgrade item ──────────────────────────────────────────────────────

    private ItemStack buildUpgradeItem(Player player, UpgradeType type) {
        int     curLevel  = plugin.getUpgradeManager().getLevel(player.getUniqueId(), type);
        int     nextLevel = curLevel + 1;
        boolean maxed     = curLevel >= type.maxLevel;

        Material mat = switch (type) {
            case ATTACK    -> Material.IRON_SWORD;
            case DEFENSE   -> Material.IRON_CHESTPLATE;
            case HEALTH    -> Material.GOLDEN_APPLE;
            case SPEED     -> Material.FEATHER;
            case LIFESTEAL -> Material.GHAST_TEAR;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        String nameColor = maxed ? "§a" : "§f";
        meta.displayName(LEGACY.deserialize(nameColor + type.displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize("§7Level:  " + bar(curLevel, type.maxLevel)));
        lore.add(LEGACY.deserialize("§8─────────────────────"));

        if (!maxed) {
            String currentEffect = currentEffectText(type, curLevel);
            String nextEffect    = currentEffectText(type, nextLevel);
            lore.add(LEGACY.deserialize("§7Jetzt:  " + currentEffect));
            lore.add(LEGACY.deserialize("§7→ Lv" + nextLevel + ": " + nextEffect));
            lore.add(LEGACY.deserialize("§8─────────────────────"));
            lore.add(LEGACY.deserialize("§7Kosten:"));

            Map<LootItem, Integer> cost = plugin.getUpgradeManager().getCost(type, nextLevel);
            boolean canAfford = true;
            String missingItem = null;
            for (Map.Entry<LootItem, Integer> e : cost.entrySet()) {
                int    have   = plugin.getLootManager().getQuantity(player.getUniqueId(), e.getKey());
                String col    = have >= e.getValue() ? "§a" : "§c";
                lore.add(LEGACY.deserialize("  " + col + e.getValue() + "x §f"
                    + e.getKey().displayName
                    + " §8(§f" + have + " vorhanden§8)"));
                if (have < e.getValue()) {
                    canAfford = false;
                    if (missingItem == null) missingItem = e.getKey().displayName;
                }
            }
            lore.add(LEGACY.deserialize("§8─────────────────────"));
            if (canAfford) {
                lore.add(LEGACY.deserialize("§a▶ Klicken!"));
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(LEGACY.deserialize("§c✗ Fehlt: " + missingItem));
            }
        } else {
            lore.add(LEGACY.deserialize("§7Jetzt:  " + currentEffectText(type, curLevel)));
            lore.add(LEGACY.deserialize("§8─────────────────────"));
            lore.add(LEGACY.deserialize("§a✔ MAX"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Status pane (slot next to upgrade) ────────────────────────────────

    private ItemStack buildStatusPane(Player player, UpgradeType type) {
        int     curLevel = plugin.getUpgradeManager().getLevel(player.getUniqueId(), type);
        boolean maxed    = curLevel >= type.maxLevel;

        if (maxed) {
            ItemStack pane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
            ItemMeta  meta = pane.getItemMeta();
            meta.displayName(LEGACY.deserialize("§5§lMAX"));
            pane.setItemMeta(meta);
            return pane;
        }

        Map<LootItem, Integer> cost = plugin.getUpgradeManager().getCost(type, curLevel + 1);
        boolean canAfford = cost.entrySet().stream().allMatch(e ->
            plugin.getLootManager().getQuantity(player.getUniqueId(), e.getKey()) >= e.getValue());

        Material mat  = canAfford ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String   name = canAfford ? "§a§l▶ Kaufbar" : "§c§l✗ Zu teuer";
        ItemStack pane = new ItemStack(mat);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        pane.setItemMeta(meta);
        return pane;
    }

    // ── Resource info item ─────────────────────────────────────────────────

    private ItemStack buildItemInfo(Player player, LootItem lootItem, Material mat) {
        int       qty  = plugin.getLootManager().getQuantity(player.getUniqueId(), lootItem);
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(lootItem.color + lootItem.displayName));
        meta.lore(List.of(
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Vorrat: §f" + qty),
            LEGACY.deserialize("§8─────────────────────")
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
        if (!(event.getInventory().getHolder() instanceof UpgradeGui)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) { player.closeInventory(); return; }

        UUID uid = player.getUniqueId();
        if (upgrading.contains(uid)) return;

        UpgradeType[] types = UpgradeType.values();
        for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
            if (slot == UPGRADE_SLOTS[i]) {
                final UpgradeType type = types[i];
                upgrading.add(uid);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getUpgradeManager().tryUpgrade(player, type);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        upgrading.remove(uid);
                        if (player.isOnline()) open(player);
                    });
                });
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

    private static String currentEffectText(UpgradeType type, int level) {
        return switch (type) {
            case ATTACK    -> "§c+" + Math.round((1.0 + 0.08 * level - 1.0) * 100)
                              + "% §7Schaden §8(Schwert, Bogen, Axt & Feuerball)";
            case DEFENSE   -> "§a-" + Math.round((1.0 - Math.max(0.25, 1.0 - 0.015 * level)) * 100)
                              + "% §7eingehender Schaden";
            case HEALTH    -> "§e+" + (level * 6) + " §7Max-HP total";
            case SPEED     -> "§b+" + (level * 3) + "% §7Geschwindigkeit total";
            case LIFESTEAL -> "§5+" + (level * 5) + "% §7Lebensraub total";
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
