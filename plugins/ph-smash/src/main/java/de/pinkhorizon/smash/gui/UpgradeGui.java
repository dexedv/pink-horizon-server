package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import de.pinkhorizon.smash.managers.UpgradeManager.UpgradeType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UpgradeGui implements Listener, InventoryHolder {

    private final PHSmash plugin;

    public UpgradeGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() { return null; }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 54, "§c§lUpgrades – Smash the Boss");
        fill(inv, player);
        player.openInventory(inv);
    }

    private void fill(Inventory inv, Player player) {
        UpgradeType[] types = UpgradeType.values();
        int[] slots = {10, 19, 28, 37, 46}; // linke Spalte, jeder Typ eine Zeile

        for (int i = 0; i < types.length; i++) {
            UpgradeType type = types[i];
            inv.setItem(slots[i], buildUpgradeItem(player, type));
        }

        // Item-Vorräte (rechte Seite, Slot 14–17)
        inv.setItem(15, buildItemInfo(player, LootItem.IRON_FRAGMENT,   Material.IRON_INGOT));
        inv.setItem(24, buildItemInfo(player, LootItem.GOLD_FRAGMENT,   Material.GOLD_INGOT));
        inv.setItem(33, buildItemInfo(player, LootItem.DIAMOND_SHARD,   Material.DIAMOND));
        inv.setItem(42, buildItemInfo(player, LootItem.BOSS_CORE,       Material.NETHER_STAR));

        // Schließen-Button
        inv.setItem(49, buildClose());

        // Glasfüller
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.displayName(net.kyori.adventure.text.Component.empty());
        glass.setItemMeta(gm);
        for (int s = 0; s < 54; s++) {
            if (inv.getItem(s) == null) inv.setItem(s, glass);
        }
    }

    private ItemStack buildUpgradeItem(Player player, UpgradeType type) {
        int curLevel  = plugin.getUpgradeManager().getLevel(player.getUniqueId(), type);
        int nextLevel = curLevel + 1;
        boolean maxed = curLevel >= type.maxLevel;

        Material mat = switch (type) {
            case ATTACK    -> Material.IRON_SWORD;
            case DEFENSE   -> Material.IRON_CHESTPLATE;
            case HEALTH    -> Material.GOLDEN_APPLE;
            case SPEED     -> Material.FEATHER;
            case LIFESTEAL -> Material.GHAST_TEAR;
        };

        ItemStack item = new ItemStack(maxed ? Material.BARRIER : mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().deserialize(type.displayName + (maxed ? " §8(MAX)" : "")));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().deserialize("§7Level: §f" + curLevel + "§8/§f" + type.maxLevel));

        if (!maxed) {
            String effect = switch (type) {
                case ATTACK    -> "§c+" + (nextLevel * 10) + "% §7Schaden total";
                case DEFENSE   -> "§a-" + (nextLevel * 3) + "% §7eingehender Schaden";
                case HEALTH    -> "§e+" + (nextLevel * 4) + " §7Max-HP total";
                case SPEED     -> "§b+" + (nextLevel * 5) + "% §7Geschwindigkeit total";
                case LIFESTEAL -> "§5+" + (nextLevel * 4) + "% §7Lebensraub total";
            };
            lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize("§7Nächstes Level: " + effect));
            lore.add(net.kyori.adventure.text.Component.empty());

            Map<LootItem, Integer> cost = plugin.getUpgradeManager().getCost(type, nextLevel);
            lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize("§7Kosten:"));
            for (Map.Entry<LootItem, Integer> e : cost.entrySet()) {
                int have   = plugin.getLootManager().getQuantity(player.getUniqueId(), e.getKey());
                String col = have >= e.getValue() ? "§a" : "§c";
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("  " + col + e.getValue() + "x §7"
                        + e.getKey().displayName + " §8(" + col + have + "§8)"));
            }

            boolean canAfford = cost.entrySet().stream().allMatch(e ->
                plugin.getLootManager().getQuantity(player.getUniqueId(), e.getKey()) >= e.getValue());

            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(canAfford ? "§aKlicken zum Aufwerten!" : "§cNicht genug Items!"));

            if (canAfford) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildItemInfo(Player player, LootItem lootItem, Material mat) {
        int qty  = plugin.getLootManager().getQuantity(player.getUniqueId(), lootItem);
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().deserialize(lootItem.color + lootItem.displayName));
        meta.lore(List.of(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().deserialize("§7Vorrat: §f" + qty)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildClose() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().deserialize("§cSchließen"));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof UpgradeGui)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) { player.closeInventory(); return; }

        UpgradeType[] types = UpgradeType.values();
        int[] slots = {10, 19, 28, 37, 46};
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                plugin.getUpgradeManager().tryUpgrade(player, types[i]);
                // GUI neu laden (nächsten Tick damit DB-Update durch ist)
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 1L);
                return;
            }
        }
    }
}
