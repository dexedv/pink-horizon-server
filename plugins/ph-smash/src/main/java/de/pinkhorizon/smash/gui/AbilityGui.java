package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.AbilityManager.AbilityType;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AbilityGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // 54-slot layout:
    //  Row 1 (10-14): Allgemein
    //  Row 2 (19-21): Schwert
    //  Row 3 (28-31): Bogen
    //  Row 4 (37-39): Axt
    //  Row 5 (46)   : Passiv (IMMUN)
    private static final int[] SLOTS = {
        10, 11, 12, 13, 14,   // Allgemein
        19, 20, 21,            // Schwert
        28, 29, 30, 31,        // Bogen
        37, 38, 39,            // Axt
        46, 47, 48,            // Feuerball
        50                     // Passiv
    };
    private static final AbilityType[] ORDER = {
        AbilityType.BERSERKER, AbilityType.DODGE, AbilityType.HEAL_ON_KILL,
        AbilityType.COIN_BOOST, AbilityType.REGEN,
        AbilityType.KRITISCH, AbilityType.HINRICHTUNG, AbilityType.WIRBELWIND,
        AbilityType.EXPLOSIVE, AbilityType.BOGENSTAERKE,
        AbilityType.MEHRFACHSCHUSS, AbilityType.GIFTPFEIL,
        AbilityType.BLUTUNG, AbilityType.KLAFFENDE_WUNDE, AbilityType.TIEFE_HIEBE,
        AbilityType.FEUERKRAFT, AbilityType.VERBRENNUNG, AbilityType.DOPPELFEUER,
        AbilityType.IMMUN
    };
    private static final Material[] ICONS = {
        Material.IRON_SWORD,              // BERSERKER
        Material.FEATHER,                 // DODGE
        Material.GOLDEN_APPLE,            // HEAL_ON_KILL
        Material.GOLD_INGOT,              // COIN_BOOST
        Material.GLISTERING_MELON_SLICE,  // REGEN
        Material.DIAMOND_SWORD,           // KRITISCH
        Material.NETHERITE_SWORD,         // HINRICHTUNG
        Material.WIND_CHARGE,             // WIRBELWIND
        Material.TNT,                     // EXPLOSIVE
        Material.BOW,                     // BOGENSTAERKE
        Material.SPECTRAL_ARROW,          // MEHRFACHSCHUSS
        Material.SLIME_BALL,              // GIFTPFEIL
        Material.IRON_AXE,                // BLUTUNG
        Material.NETHERITE_AXE,           // KLAFFENDE_WUNDE
        Material.DIAMOND_AXE,             // TIEFE_HIEBE
        Material.FIRE_CHARGE,             // FEUERKRAFT
        Material.MAGMA_CREAM,             // VERBRENNUNG
        Material.BLAZE_ROD,               // DOPPELFEUER
        Material.SHIELD                   // IMMUN
    };

    private final PHSmash   plugin;
    private final Set<UUID> upgrading = new HashSet<>();

    public AbilityGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(), 54,
            LEGACY.deserialize("§6§l★ Fähigkeiten §8– §eMünz-Upgrades"));
        fillGui(inv, player);
        player.openInventory(inv);
    }

    // ── GUI füllen ─────────────────────────────────────────────────────────

    private void fillGui(Inventory inv, Player player) {
        // Gray pane background
        ItemStack pane = makePane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        // Row 0: dark glass border
        ItemStack darkPane = makePane(Material.BLACK_STAINED_GLASS_PANE);
        for (int s = 0; s < 9; s++) inv.setItem(s, darkPane);

        UUID uid   = player.getUniqueId();
        long coins = plugin.getCoinManager().getCoins(uid);
        int ironQty    = plugin.getLootManager().getQuantity(uid, LootItem.IRON_FRAGMENT);
        int goldQty    = plugin.getLootManager().getQuantity(uid, LootItem.GOLD_FRAGMENT);
        int crystalQty = plugin.getLootManager().getQuantity(uid, LootItem.DIAMOND_SHARD);
        int coreQty    = plugin.getLootManager().getQuantity(uid, LootItem.BOSS_CORE);

        // Slot 4: coin + item summary
        inv.setItem(4, makeItem(Material.GOLD_NUGGET,
            "§e§lMünzen: §f" + coins,
            List.of(
                "§8─────────────────────",
                "§7Verdiene Münzen durch Boss-Kills",
                "§8─────────────────────",
                "§7Materialien:",
                "§7  §fEisen-Splitter: §7" + ironQty,
                "§7  §6Gold-Splitter:  §7" + goldQty,
                "§7  §bBoss-Kristall:  §7" + crystalQty,
                "§7  §5Boss-Kern:      §7" + coreQty
            )));

        // Kategorie-Labels (Trenn-Panes mit Text)
        inv.setItem(9,  makeLabelPane(Material.LIME_STAINED_GLASS_PANE,   "§a§l✦ Allgemein"));
        inv.setItem(18, makeLabelPane(Material.RED_STAINED_GLASS_PANE,    "§c§l⚔ Schwert"));
        inv.setItem(27, makeLabelPane(Material.YELLOW_STAINED_GLASS_PANE, "§e§l🏹 Bogen"));
        inv.setItem(36, makeLabelPane(Material.CYAN_STAINED_GLASS_PANE,   "§b§l🪓 Axt"));
        inv.setItem(45, makeLabelPane(Material.ORANGE_STAINED_GLASS_PANE, "§6§l🔥 Feuerball"));
        inv.setItem(49, makeLabelPane(Material.PURPLE_STAINED_GLASS_PANE, "§d§l🛡 Passiv"));

        // Resource info items (rechte Spalte)
        inv.setItem(15, buildItemInfo(uid, LootItem.IRON_FRAGMENT,  Material.IRON_INGOT,  ironQty));
        inv.setItem(24, buildItemInfo(uid, LootItem.GOLD_FRAGMENT,  Material.GOLD_INGOT,  goldQty));
        inv.setItem(33, buildItemInfo(uid, LootItem.DIAMOND_SHARD,  Material.DIAMOND,     crystalQty));
        inv.setItem(42, buildItemInfo(uid, LootItem.BOSS_CORE,      Material.NETHER_STAR, coreQty));

        // Fähigkeits-Items
        for (int i = 0; i < ORDER.length; i++) {
            AbilityType type     = ORDER[i];
            int         level    = plugin.getAbilityManager().getLevel(uid, type);
            long        cost     = type.nextCost(level);
            boolean     maxed    = level >= type.maxLevel;
            boolean     canAfford = !maxed && coins >= cost;

            List<String> lore = buildAbilityLore(type, level, cost, coins, maxed, canAfford);
            String nameColor = maxed ? "§a" : canAfford ? "§e" : "§c";
            inv.setItem(SLOTS[i], makeItem(ICONS[i], nameColor + type.displayName, lore));
        }

        // Close button
        inv.setItem(53, makeItem(Material.BARRIER, "§cSchließen", List.of()));
    }

    private List<String> buildAbilityLore(AbilityType type, int level, long cost,
                                           long coins, boolean maxed, boolean canAfford) {
        List<String> lore = new ArrayList<>();
        lore.add("§8─────────────────────");
        lore.add("§7" + type.effectDesc);
        lore.add("§8─────────────────────");
        lore.add("§7Level:  " + bar(level, type.maxLevel));
        lore.add("§7Effekt: " + effectValue(type, level));
        lore.add("§8─────────────────────");

        if (!maxed) {
            lore.add("§7Kosten: §e" + cost + " §7Münzen");
            lore.add("§7Guthaben: §e" + coins);
            lore.add("§8─────────────────────");
            lore.add(canAfford ? "§a▶ Klicken zum Upgraden" : "§c✗ Nicht genug Münzen");
        } else {
            lore.add("§8─────────────────────");
            lore.add("§a✔ MAX Level!");
        }
        return lore;
    }

    private static String effectValue(AbilityType type, int level) {
        return switch (type) {
            case BERSERKER      -> "§c+" + (level * 8) + "% §7Schaden bei <35% HP";
            case DODGE          -> "§b" + (level * 4) + "% §7Ausweich-Chance";
            case HEAL_ON_KILL   -> "§4+" + (level * 8) + "% §7max-HP Heilung";
            case EXPLOSIVE      -> "§6" + (level * 7) + "% §7Chance 2× Pfeil-Schaden";
            case COIN_BOOST     -> "§e+" + (level * 20) + "% §7Münzen";
            case REGEN          -> "§a+" + String.format("%.1f", level * 1.5) + " §7HP/5s";
            case IMMUN          -> "§d" + level + "% §7Effekt-Resist";
            case KRITISCH       -> "§c" + (level * 3) + "% §7Krit-Chance §8(2,5× Schaden)";
            case HINRICHTUNG    -> "§4+" + (level * 10) + "% §7Schwert-Schaden §8(<25% Boss-HP)";
            case WIRBELWIND     -> "§6" + (level * 5) + "% §7Chance: Doppel-Treffer §8(50% Schaden)";
            case BOGENSTAERKE   -> "§a+" + (level * 5) + "% §7Bogen-Schaden";
            case MEHRFACHSCHUSS -> "§e" + (level * 4) + "% §7Chance: 2. Pfeil §8(80% Schaden)";
            case GIFTPFEIL      -> "§2" + (level * 5) + "% §7Chance: Gift-DOT §8(3× 5% des Treffers)";
            case BLUTUNG         -> "§c" + (level * 5) + "% §7Blutungs-Chance §8(" + (3 + AbilityType.TIEFE_HIEBE.maxLevel) + " Ticks max × 6%)";
            case KLAFFENDE_WUNDE -> "§4+" + (level * 10) + "% §7Blut-DOT-Schaden §8(Multiplikator)";
            case TIEFE_HIEBE     -> "§7+" + level + " §7Tick(s) §8(Gesamt: " + (3 + level) + " Ticks)";
            case FEUERKRAFT      -> "§6+" + (level * 8) + "% §7Feuerball-Schaden";
            case VERBRENNUNG     -> "§c" + (level * 7) + "% §7Chance: Brand-DOT §8(3× 8% des Treffers)";
            case DOPPELFEUER     -> "§e" + (level * 5) + "% §7Chance: 2. Feuerball §8(80% Schaden)";
        };
    }

    // ── Klick-Handler ──────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();

        // Close button
        if (slot == 53) { player.closeInventory(); return; }

        UUID uid = player.getUniqueId();
        if (upgrading.contains(uid)) return;

        for (int i = 0; i < SLOTS.length; i++) {
            if (slot != SLOTS[i]) continue;
            AbilityType type = ORDER[i];
            Inventory inv = event.getInventory();
            upgrading.add(uid);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean ok = plugin.getAbilityManager().tryUpgrade(player, type);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    upgrading.remove(uid);
                    if (ok && player.isOnline()) {
                        fillGui(inv, player);
                        plugin.getScoreboardManager().update(player);
                    }
                });
            });
            break;
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private ItemStack buildItemInfo(UUID uid, LootItem lootItem, Material mat, int qty) {
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

    private ItemStack makeLabelPane(Material mat, String name) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack makePane(Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        meta.lore(lore.stream().map(LEGACY::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static String bar(int current, int max) {
        int len    = 15;
        int filled = max > 0 ? Math.min(len, (int) Math.round(len * (double) current / max)) : 0;
        String col = filled >= len ? "§a" : filled >= len / 2 ? "§e" : "§c";
        return col + "█".repeat(filled) + "§8" + "█".repeat(len - filled)
            + " §7(" + current + "/" + max + ")";
    }

    public static class GuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
