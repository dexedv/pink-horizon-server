package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
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
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShopGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // ── Shop-Einträge ──────────────────────────────────────────────────────

    private record ShopEntry(
        String    name,
        Material  mat,
        long      cost,
        String[]  loreExtra,
        // null = kein Trank (normales Item)
        PotionEffectType potionType,
        int              duration,   // in Ticks
        int              amplifier
    ) {}

    private static final int TICKS_45S  = 900;
    private static final int TICKS_90S  = 1800;
    private static final int TICKS_5MIN = 6000;

    // ── 6 Tränke (Slots 10–15) ─────────────────────────────────────────────
    private static final ShopEntry[] POTIONS = {
        new ShopEntry("§c§lSofort-Heilung II",   Material.POTION, 40,
            new String[]{"§7+8 HP sofort", "§7Direkte Wirkung beim Trinken"},
            PotionEffectType.INSTANT_HEALTH, 1, 1),

        new ShopEntry("§a§lRegeneration II",      Material.POTION, 60,
            new String[]{"§7HP-Regen für §e45 Sekunden", "§7Hält auch während des Kampfes"},
            PotionEffectType.REGENERATION, TICKS_45S, 1),

        new ShopEntry("§c§lStärke II",            Material.POTION, 80,
            new String[]{"§7+2 Stärke für §e1:30 Min", "§7Erhöht den Schaden"},
            PotionEffectType.STRENGTH, TICKS_90S, 1),

        new ShopEntry("§b§lWiderstand II",        Material.POTION, 100,
            new String[]{"§7-40% Schaden für §e1:30 Min", "§7Sehr nützlich gegen starke Bosse"},
            PotionEffectType.RESISTANCE, TICKS_90S, 1),

        new ShopEntry("§e§lGeschwindigkeit II",   Material.POTION, 50,
            new String[]{"§7+Speed für §e1:30 Min", "§7Ausweichen leichter"},
            PotionEffectType.SPEED, TICKS_90S, 1),

        new ShopEntry("§6§lFeuerschutz",          Material.POTION, 30,
            new String[]{"§7Feuerschutz für §e5 Min", "§7Nützlich gegen Feuerschaden"},
            PotionEffectType.FIRE_RESISTANCE, TICKS_5MIN, 0),
    };

    // ── 2 Spezial-Items (Slots 19–20) ─────────────────────────────────────
    private static final ShopEntry[] SPECIALS = {
        new ShopEntry("§6§lGoldener Apfel",       Material.GOLDEN_APPLE, 120,
            new String[]{"§7+4 HP Absorption + Regen", "§7Sofortiger Effekt beim Essen"},
            null, 0, 0),

        new ShopEntry("§5§lVerzauberter Apfel",   Material.ENCHANTED_GOLDEN_APPLE, 500,
            new String[]{"§7+4 HP Absorption", "§7Regen IV + Widerstand + Feuerschutz",
                         "§8Selten – maximale Wirkung"},
            null, 0, 0),
    };

    // ──────────────────────────────────────────────────────────────────────

    private final PHSmash plugin;

    public ShopGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(), 54,
            LEGACY.deserialize("§6§l☆ Smash-Shop §8– §eMünzen ausgeben"));
        fill(inv, player);
        player.openInventory(inv);
    }

    // ── GUI füllen ─────────────────────────────────────────────────────────

    private void fill(Inventory inv, Player player) {
        // Gray filler
        ItemStack gray = makePane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setItem(i, gray);

        // Row 0: dark border
        ItemStack dark = makePane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, dark);

        long coins = plugin.getCoinManager().getCoins(player.getUniqueId());

        // Slot 4: coin display
        inv.setItem(4, makeItem(Material.GOLD_NUGGET,
            "§e§lDeine Münzen: §f" + coins,
            List.of("§7Kaufe nützliche Items für den Kampf")));

        // Category label: Tränke (slot 9)
        inv.setItem(9, makeLabelPane(Material.CYAN_STAINED_GLASS_PANE, "§b§l⚗ Tränke"));

        // Potions at slots 10–15
        for (int i = 0; i < POTIONS.length; i++) {
            boolean canAfford = coins >= POTIONS[i].cost();
            inv.setItem(10 + i, buildEntry(POTIONS[i], canAfford));
        }

        // Category label: Spezial (slot 18)
        inv.setItem(18, makeLabelPane(Material.ORANGE_STAINED_GLASS_PANE, "§6§l★ Spezial-Items"));

        // Specials at slots 19–20
        for (int i = 0; i < SPECIALS.length; i++) {
            boolean canAfford = coins >= SPECIALS[i].cost();
            inv.setItem(19 + i, buildEntry(SPECIALS[i], canAfford));
        }

        // Close button
        inv.setItem(49, makeItem(Material.BARRIER, "§cSchließen", List.of()));
    }

    private ItemStack buildEntry(ShopEntry e, boolean canAfford) {
        ItemStack item = e.potionType() != null
            ? buildPotion(e)
            : new ItemStack(e.mat());

        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(e.name()));

        var lore = new ArrayList<String>(Arrays.asList(e.loreExtra()));
        lore.add("§8─────────────────────");
        lore.add("§7Kosten: §e" + e.cost() + " §7Münzen");
        lore.add(canAfford ? "§a▶ Klicken zum Kaufen" : "§c✗ Nicht genug Münzen");
        meta.lore(lore.stream().map(LEGACY::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPotion(ShopEntry e) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta pm    = (PotionMeta) potion.getItemMeta();
        pm.addCustomEffect(
            new PotionEffect(e.potionType(), e.duration(), e.amplifier(), false, true, true),
            true);
        potion.setItemMeta(pm);
        return potion;
    }

    // ── Klick-Handler ──────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) { player.closeInventory(); return; }

        // Resolve entry from slot
        ShopEntry entry = null;
        if (slot >= 10 && slot < 10 + POTIONS.length) {
            entry = POTIONS[slot - 10];
        } else if (slot >= 19 && slot < 19 + SPECIALS.length) {
            entry = SPECIALS[slot - 19];
        }
        if (entry == null) return;

        final ShopEntry finalEntry = entry;
        final Inventory inv = event.getInventory();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean paid = plugin.getCoinManager().spendCoins(player.getUniqueId(), finalEntry.cost());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (paid) {
                    ItemStack give = finalEntry.potionType() != null
                        ? buildPotion(finalEntry)
                        : new ItemStack(finalEntry.mat());
                    player.getInventory().addItem(give).values()
                        .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                    player.sendMessage("§a✔ §7Gekauft: §f" + stripColor(finalEntry.name()));
                    plugin.getScoreboardManager().update(player);
                    fill(inv, player);
                } else {
                    player.sendMessage("§c✗ §7Nicht genug Münzen!");
                }
            });
        });
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private ItemStack makePane(Material mat) {
        ItemStack p = new ItemStack(mat);
        ItemMeta  m = p.getItemMeta();
        m.displayName(Component.empty());
        p.setItemMeta(m);
        return p;
    }

    private ItemStack makeLabelPane(Material mat, String name) {
        ItemStack p = new ItemStack(mat);
        ItemMeta  m = p.getItemMeta();
        m.displayName(LEGACY.deserialize(name));
        p.setItemMeta(m);
        return p;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta  m = i.getItemMeta();
        m.displayName(LEGACY.deserialize(name));
        m.lore(lore.stream().map(LEGACY::deserialize).toList());
        i.setItemMeta(m);
        return i;
    }

    private static String stripColor(String s) {
        return s.replaceAll("§[0-9a-fk-or]", "");
    }

    public static class ShopHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
