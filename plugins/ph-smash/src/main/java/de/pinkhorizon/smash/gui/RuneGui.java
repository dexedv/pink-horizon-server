package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import de.pinkhorizon.smash.managers.RuneManager.RuneType;
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

public class RuneGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Rune item slots (one per row, left column)
    private static final int[] RUNE_SLOTS   = {10, 19, 28};
    // Status pane slots (right next to each rune)
    private static final int[] STATUS_SLOTS = {11, 20, 29};
    // Resource info slots (far right column per row)
    private static final int[] RES_SLOTS    = {15, 24, 33};

    private final PHSmash plugin;

    public RuneGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    // ── Inventory holder ──────────────────────────────────────────────────

    public static class RuneHolder implements InventoryHolder {
        private final UUID playerUuid;
        RuneHolder(UUID playerUuid) { this.playerUuid = playerUuid; }
        public UUID getPlayerUuid() { return playerUuid; }
        @Override public Inventory getInventory() { return null; }
    }

    // ── Open / fill ───────────────────────────────────────────────────────

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(
            new RuneHolder(player.getUniqueId()),
            54,
            LEGACY.deserialize("§c§l⚡ Runen §8– §7Schmieden & Aktivieren"));
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

        // ── Slot 4: title / info item ──────────────────────────────────────
        inv.setItem(4, buildTitleItem());

        // ── Rune items + status panes + resource info ──────────────────────
        RuneType[] types = RuneType.values();
        UUID uuid = player.getUniqueId();
        for (int i = 0; i < types.length; i++) {
            inv.setItem(RUNE_SLOTS[i],   buildRuneItem(player, types[i], uuid));
            inv.setItem(STATUS_SLOTS[i], buildStatusPane(player, types[i], uuid));
            inv.setItem(RES_SLOTS[i],    buildResourceInfo(uuid, types[i]));
        }

        // ── Close button ───────────────────────────────────────────────────
        inv.setItem(49, buildClose());
    }

    // ── Title item ─────────────────────────────────────────────────────────

    private ItemStack buildTitleItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§c§l⚡ Runen-Schmiede"));
        meta.lore(List.of(
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Runen verleihen temporäre Boni"),
            LEGACY.deserialize("§7für die nächsten §f3 Bosse§7."),
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Jeder besiegte Boss verbraucht"),
            LEGACY.deserialize("§71 Ladung aller aktiven Runen."),
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Schmieden §8→ §f+3 Ladungen")
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ── Rune item ─────────────────────────────────────────────────────────

    private ItemStack buildRuneItem(Player player, RuneType type, UUID uuid) {
        int charges   = plugin.getRuneManager().getCharges(uuid, type);
        boolean active = charges > 0;
        boolean canAfford = canAffordRune(uuid, type);

        ItemStack item = new ItemStack(type.icon);
        ItemMeta  meta = item.getItemMeta();

        String nameColor = active ? "§a" : "§7";
        meta.displayName(LEGACY.deserialize(nameColor + type.displayName
            + (active ? " §8[§aAktiv§8]" : " §8[§cInaktiv§8]")));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize(type.effectDesc));
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        String chargeColor = active ? "§a" : "§c";
        lore.add(LEGACY.deserialize("§7Ladungen: " + chargesBar(charges) + " " + chargeColor + charges + "§8/§f3"));
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize("§7Schmieden kostet:"));
        lore.add(LEGACY.deserialize("  " + getCostLine(uuid, type)));
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        if (canAfford) {
            lore.add(LEGACY.deserialize("§a▶ Klicken zum Schmieden §8(+3 Ladungen)"));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(LEGACY.deserialize("§c✗ Nicht genug Materialien"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String chargesBar(int charges) {
        int max    = 3;
        int filled = Math.min(charges, max);
        String col = filled >= max ? "§a" : filled > 0 ? "§e" : "§c";
        return col + "█".repeat(filled) + "§8" + "█".repeat(Math.max(0, max - filled));
    }

    // ── Status pane ───────────────────────────────────────────────────────

    private ItemStack buildStatusPane(Player player, RuneType type, UUID uuid) {
        boolean canAfford = canAffordRune(uuid, type);
        int charges = plugin.getRuneManager().getCharges(uuid, type);

        Material mat;
        String   name;
        List<Component> lore = new ArrayList<>();

        if (canAfford) {
            mat  = Material.LIME_STAINED_GLASS_PANE;
            name = "§a§l▶ Schmiedbar";
        } else {
            mat  = Material.RED_STAINED_GLASS_PANE;
            name = "§c§l✗ Zu teuer";
            lore.add(LEGACY.deserialize(getMissingLine(uuid, type)));
        }

        if (charges > 0) {
            lore.add(LEGACY.deserialize("§a§l● Rune aktiv"));
        }

        ItemStack pane = new ItemStack(mat);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        if (!lore.isEmpty()) meta.lore(lore);
        pane.setItemMeta(meta);
        return pane;
    }

    // ── Resource info item ─────────────────────────────────────────────────

    private ItemStack buildResourceInfo(UUID uuid, RuneType type) {
        return switch (type) {
            case WAR_RUNE -> {
                int qty = plugin.getLootManager().getQuantity(uuid, LootItem.IRON_FRAGMENT);
                yield resItem(Material.IRON_INGOT, "§7Eisen-Splitter", qty, 5);
            }
            case SHIELD_RUNE -> {
                int qty = plugin.getLootManager().getQuantity(uuid, LootItem.GOLD_FRAGMENT);
                yield resItem(Material.GOLD_INGOT, "§6Gold-Splitter", qty, 5);
            }
            case LUCK_RUNE -> {
                int qty = plugin.getLootManager().getQuantity(uuid, LootItem.DIAMOND_SHARD);
                yield resItem(Material.DIAMOND, "§bBoss-Kristall", qty, 2);
            }
        };
    }

    private ItemStack resItem(Material mat, String name, int qty, int need) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        String col = qty >= need ? "§a" : "§c";
        meta.lore(List.of(
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Vorrat: " + col + qty),
            LEGACY.deserialize("§7Benötigt: §f" + need + " §7zum Schmieden"),
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
        if (!(event.getInventory().getHolder() instanceof RuneHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) { player.closeInventory(); return; }

        RuneType[] types = RuneType.values();
        for (int i = 0; i < RUNE_SLOTS.length; i++) {
            if (slot == RUNE_SLOTS[i]) {
                RuneType type = types[i];
                plugin.getRuneManager().craftRune(player, type, plugin);
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 1L);
                return;
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean canAffordRune(UUID uuid, RuneType type) {
        return switch (type) {
            case WAR_RUNE    -> plugin.getLootManager().getQuantity(uuid, LootItem.IRON_FRAGMENT) >= 5;
            case SHIELD_RUNE -> plugin.getLootManager().getQuantity(uuid, LootItem.GOLD_FRAGMENT) >= 5;
            case LUCK_RUNE   -> plugin.getLootManager().getQuantity(uuid, LootItem.DIAMOND_SHARD) >= 2;
        };
    }

    private String getCostLine(UUID uuid, RuneType type) {
        return switch (type) {
            case WAR_RUNE -> {
                int have = plugin.getLootManager().getQuantity(uuid, LootItem.IRON_FRAGMENT);
                yield (have >= 5 ? "§a" : "§c") + "5x §7Eisen-Splitter §8(§f" + have + " vorhanden§8)";
            }
            case SHIELD_RUNE -> {
                int have = plugin.getLootManager().getQuantity(uuid, LootItem.GOLD_FRAGMENT);
                yield (have >= 5 ? "§a" : "§c") + "5x §7Gold-Splitter §8(§f" + have + " vorhanden§8)";
            }
            case LUCK_RUNE -> {
                int have = plugin.getLootManager().getQuantity(uuid, LootItem.DIAMOND_SHARD);
                yield (have >= 2 ? "§a" : "§c") + "2x §7Boss-Kristall §8(§f" + have + " vorhanden§8)";
            }
        };
    }

    private String getMissingLine(UUID uuid, RuneType type) {
        return switch (type) {
            case WAR_RUNE -> {
                int miss = 5 - plugin.getLootManager().getQuantity(uuid, LootItem.IRON_FRAGMENT);
                yield "§7Fehlt: §c" + miss + " §7Eisen-Splitter";
            }
            case SHIELD_RUNE -> {
                int miss = 5 - plugin.getLootManager().getQuantity(uuid, LootItem.GOLD_FRAGMENT);
                yield "§7Fehlt: §c" + miss + " §7Gold-Splitter";
            }
            case LUCK_RUNE -> {
                int miss = 2 - plugin.getLootManager().getQuantity(uuid, LootItem.DIAMOND_SHARD);
                yield "§7Fehlt: §c" + miss + " §7Boss-Kristall";
            }
        };
    }

    private ItemStack makePure(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }
}
