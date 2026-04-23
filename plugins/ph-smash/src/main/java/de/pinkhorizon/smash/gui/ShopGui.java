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

import java.util.List;

public class ShopGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // ── Shop-Einträge ──────────────────────────────────────────────────────

    private record ShopEntry(
        int       slot,
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

    private static final ShopEntry[] ENTRIES = {
        new ShopEntry(10, "§c§lSofort-Heilung II",   Material.POTION, 40,
            new String[]{"§7+8 HP sofort", "§7Direkt trinken – sofortige Wirkung"},
            PotionEffectType.INSTANT_HEALTH, 1, 1),

        new ShopEntry(12, "§a§lRegeneration II",      Material.POTION, 60,
            new String[]{"§7HP-Regen für §e45 Sekunden", "§7Hält auch während des Kampfes"},
            PotionEffectType.REGENERATION, TICKS_45S, 1),

        new ShopEntry(14, "§c§lStärke II",            Material.POTION, 80,
            new String[]{"§7+2 Stärke für §e1:30 Min", "§7Erhöht den Schaden"},
            PotionEffectType.STRENGTH, TICKS_90S, 1),

        new ShopEntry(16, "§b§lWiderstand II",        Material.POTION, 100,
            new String[]{"§7-40% Schaden für §e1:30 Min", "§7Sehr nützlich gegen starke Bosse"},
            PotionEffectType.RESISTANCE, TICKS_90S, 1),

        new ShopEntry(19, "§e§lGeschwindigkeit II",   Material.POTION, 50,
            new String[]{"§7+Speed für §e1:30 Min", "§7Ausweichen leichter"},
            PotionEffectType.SPEED, TICKS_90S, 1),

        new ShopEntry(21, "§6§lFeuerschutz",          Material.POTION, 30,
            new String[]{"§7Feuerschutz für §e5 Min", "§7Nützlich gegen Feuerschaden"},
            PotionEffectType.FIRE_RESISTANCE, TICKS_5MIN, 0),

        new ShopEntry(23, "§6§lGoldener Apfel",       Material.GOLDEN_APPLE, 120,
            new String[]{"§7+4 HP Absorption + Regen", "§7Sofortiger Effekt beim Essen"},
            null, 0, 0),

        new ShopEntry(25, "§5§lVerzauberter Apfel",   Material.ENCHANTED_GOLDEN_APPLE, 500,
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
        Inventory inv = Bukkit.createInventory(new ShopHolder(), 27,
            LEGACY.deserialize("§6§l☆ Smash-Shop §8– §eMünzen ausgeben"));
        fill(inv, player);
        player.openInventory(inv);
    }

    // ── GUI füllen ─────────────────────────────────────────────────────────

    private void fill(Inventory inv, Player player) {
        ItemStack pane = makePane();
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        long coins = plugin.getCoinManager().getCoins(player.getUniqueId());

        // Münzen-Anzeige (Slot 4)
        inv.setItem(4, info(Material.GOLD_NUGGET,
            "§e§lDeine Münzen: §f" + coins,
            List.of("§7Kaufe nützliche Items für den Kampf")));

        for (ShopEntry e : ENTRIES) {
            boolean canAfford = coins >= e.cost();
            inv.setItem(e.slot(), buildEntry(e, canAfford));
        }
    }

    private ItemStack buildEntry(ShopEntry e, boolean canAfford) {
        ItemStack item = e.potionType() != null
            ? buildPotion(e)
            : new ItemStack(e.mat());

        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(e.name()));

        var lore = new java.util.ArrayList<String>();
        lore.addAll(java.util.Arrays.asList(e.loreExtra()));
        lore.add(" ");
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
        for (ShopEntry e : ENTRIES) {
            if (slot != e.slot()) continue;

            Inventory inv = event.getInventory();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean paid = plugin.getCoinManager().spendCoins(player.getUniqueId(), e.cost());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (paid) {
                        ItemStack give = e.potionType() != null ? buildPotion(e) : new ItemStack(e.mat());
                        player.getInventory().addItem(give).values()
                            .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                        player.sendMessage("§a✔ §7Gekauft: §f" + stripColor(e.name()));
                        plugin.getScoreboardManager().update(player);
                        fill(inv, player);  // Münzen-Anzeige aktualisieren
                    } else {
                        player.sendMessage("§c✗ §7Nicht genug Münzen!");
                    }
                });
            });
            break;
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private ItemStack makePane() {
        ItemStack p = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  m = p.getItemMeta();
        m.displayName(Component.empty());
        p.setItemMeta(m);
        return p;
    }

    private ItemStack info(Material mat, String name, List<String> lore) {
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
