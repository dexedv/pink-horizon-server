package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.ForgeManager.ForgeEnchant;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
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
import java.util.Set;
import java.util.UUID;

public class ForgeGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Enchant item slots (one per row, left column)
    private static final int[] ENCHANT_SLOTS = {10, 19, 28, 37, 46};
    // Status pane slots (right next to each enchant item)
    private static final int[] STATUS_SLOTS  = {11, 20, 29, 38, 47};

    private final PHSmash   plugin;
    private final Set<UUID> forging = new HashSet<>();

    public ForgeGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    // ── Inventory holder ──────────────────────────────────────────────────────

    public static class ForgeHolder implements InventoryHolder {
        private final UUID playerUuid;
        ForgeHolder(UUID playerUuid) { this.playerUuid = playerUuid; }
        public UUID getPlayerUuid() { return playerUuid; }
        @Override public Inventory getInventory() { return null; }
    }

    // ── Open / fill ───────────────────────────────────────────────────────────

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(
            new ForgeHolder(player.getUniqueId()),
            54,
            LEGACY.deserialize("§6§l⚒ Waffen-Schmiede"));
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

        // ── Enchant items + status panes ───────────────────────────────────
        ForgeEnchant[] enchants = ForgeEnchant.values();
        for (int i = 0; i < enchants.length; i++) {
            inv.setItem(ENCHANT_SLOTS[i], buildEnchantItem(player, enchants[i]));
            inv.setItem(STATUS_SLOTS[i],  buildStatusPane(player, enchants[i]));
        }

        // ── Resource info items (right column) ─────────────────────────────
        inv.setItem(15, buildItemInfo(player, LootItem.IRON_FRAGMENT, Material.IRON_INGOT));
        inv.setItem(24, buildItemInfo(player, LootItem.GOLD_FRAGMENT,  Material.GOLD_INGOT));
        inv.setItem(33, buildItemInfo(player, LootItem.DIAMOND_SHARD,  Material.DIAMOND));
        inv.setItem(42, buildItemInfo(player, LootItem.BOSS_CORE,      Material.NETHER_STAR));

        // ── Close button ───────────────────────────────────────────────────
        inv.setItem(49, buildClose());
    }

    // ── Title item (slot 4) ────────────────────────────────────────────────────

    private ItemStack buildTitleItem() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§6§l⚒ Waffen-Schmiede"));
        meta.lore(List.of(
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Schmiede mächtige Verzauberungen"),
            LEGACY.deserialize("§7für deine Waffe. Jede Verzauberung"),
            LEGACY.deserialize("§7verbraucht §fLadungen §7beim Kämpfen."),
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Materialien durch Bosse verdienen."),
            LEGACY.deserialize("§7Schmieden fügt §f+Ladungen §7hinzu.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ── Enchant item ───────────────────────────────────────────────────────────

    private ItemStack buildEnchantItem(Player player, ForgeEnchant enchant) {
        UUID    uuid     = player.getUniqueId();
        int     charges  = plugin.getForgeManager().getCharges(uuid, enchant);
        boolean active   = charges > 0;
        int     have     = plugin.getLootManager().getQuantity(uuid, enchant.costItem);
        boolean canAfford = have >= enchant.costAmount;

        Material icon = switch (enchant) {
            case SHARPNESS   -> Material.IRON_SWORD;
            case POWER       -> Material.BOW;
            case FIRE_ASPECT -> Material.BLAZE_POWDER;
            case KNOCKBACK   -> Material.PISTON;
            case LIFEDRAIN   -> Material.GHAST_TEAR;
        };

        ItemStack item = new ItemStack(icon);
        ItemMeta  meta = item.getItemMeta();

        String nameColor = active ? "§a" : "§f";
        meta.displayName(LEGACY.deserialize(nameColor + enchant.displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize(enchant.desc));

        // Active state
        if (active) {
            lore.add(LEGACY.deserialize("§7Aktiv: §aJa §8(§f" + charges + " Ladungen§8)"));
        } else {
            lore.add(LEGACY.deserialize("§7Aktiv: §cNein"));
        }

        lore.add(LEGACY.deserialize("§8─────────────────────"));

        // Cost line
        String costItemName = enchant.costItem.displayName;
        lore.add(LEGACY.deserialize("§7Kosten: §f" + enchant.costAmount + "x §7" + costItemName));

        // Current quantity with color
        String haveColor = canAfford ? "§a" : "§c";
        lore.add(LEGACY.deserialize("§7Vorrat: " + haveColor + have + " §7" + costItemName));

        lore.add(LEGACY.deserialize("§8─────────────────────"));

        // Action line
        if (canAfford) {
            lore.add(LEGACY.deserialize("§a▶ Schmieden §8(+" + enchant.chargesPerCraft + " Ladungen)"));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(LEGACY.deserialize("§c✗ Nicht genug " + costItemName));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Status pane ───────────────────────────────────────────────────────────

    private ItemStack buildStatusPane(Player player, ForgeEnchant enchant) {
        UUID    uuid      = player.getUniqueId();
        int     have      = plugin.getLootManager().getQuantity(uuid, enchant.costItem);
        boolean canAfford = have >= enchant.costAmount;

        // No PURPLE: forge enchants have no max level — always craftable in theory
        Material mat  = canAfford ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String   name = canAfford ? "§a§l▶ Schmiedbar" : "§c§l✗ Zu teuer";

        ItemStack pane = new ItemStack(mat);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        if (!canAfford) {
            int missing = enchant.costAmount - have;
            meta.lore(List.of(LEGACY.deserialize(
                "§7Fehlt: §c" + missing + "x §7" + enchant.costItem.displayName)));
        }
        pane.setItemMeta(meta);
        return pane;
    }

    // ── Resource info item ─────────────────────────────────────────────────────

    private ItemStack buildItemInfo(Player player, LootItem lootItem, Material mat) {
        int      qty  = plugin.getLootManager().getQuantity(player.getUniqueId(), lootItem);
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
        if (!(event.getInventory().getHolder() instanceof ForgeHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) { player.closeInventory(); return; }

        UUID uid = player.getUniqueId();
        if (forging.contains(uid)) return;

        ForgeEnchant[] enchants = ForgeEnchant.values();
        for (int i = 0; i < ENCHANT_SLOTS.length; i++) {
            if (slot == ENCHANT_SLOTS[i]) {
                final ForgeEnchant enchant = enchants[i];
                forging.add(uid);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getForgeManager().craft(player, enchant);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        forging.remove(uid);
                        if (player.isOnline()) open(player);
                    });
                });
                return;
            }
        }
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
