package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.AbilityManager.AbilityType;
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
import java.util.List;

public class AbilityGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // 3 Reihen = 27 Slots. Abilities in Reihe 1 und 2 (je 3 nebeneinander)
    private static final int[]        SLOTS  = {10, 12, 14, 19, 21, 23};
    private static final AbilityType[] ORDER  = AbilityType.values();

    private static final Material[] ICONS = {
        Material.IRON_SWORD,          // BERSERKER
        Material.FEATHER,             // DODGE
        Material.GOLDEN_APPLE,        // HEAL_ON_KILL
        Material.TNT,                 // EXPLOSIVE
        Material.GOLD_INGOT,          // COIN_BOOST
        Material.GLISTERING_MELON_SLICE // REGEN
    };

    private final PHSmash plugin;

    public AbilityGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(), 27,
            LEGACY.deserialize("§6§lFähigkeiten §8– §eMünz-Upgrades"));
        fillGui(inv, player);
        player.openInventory(inv);
    }

    // ── GUI füllen ─────────────────────────────────────────────────────────

    private void fillGui(Inventory inv, Player player) {
        ItemStack pane = makePane();
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        long coins = plugin.getCoinManager().getCoins(player.getUniqueId());

        // Münz-Anzeige oben Mitte
        inv.setItem(4, makeItem(Material.GOLD_NUGGET,
            "§e§lMünzen: §f" + coins,
            List.of("§7Verdiene Münzen durch Boss-Kills")));

        for (int i = 0; i < ORDER.length; i++) {
            AbilityType type  = ORDER[i];
            int         level = plugin.getAbilityManager().getLevel(player.getUniqueId(), type);
            long        cost  = type.nextCost(level);
            boolean     maxed = level >= type.maxLevel;
            boolean     canAfford = !maxed && coins >= cost;

            List<String> lore = new ArrayList<>();
            lore.add("§8" + type.effectDesc);
            lore.add(" ");
            lore.add("§7Level: §f" + level + " §8/ §f" + type.maxLevel);
            if (!maxed) {
                lore.add("§7Kosten: §e" + cost + " §7Münzen");
                lore.add(canAfford ? "§a▶ Klicken zum Upgraden" : "§c✗ Nicht genug Münzen");
            } else {
                lore.add("§a✔ Maximales Level erreicht!");
            }

            String nameColor = maxed ? "§a" : canAfford ? "§e" : "§c";
            inv.setItem(SLOTS[i], makeItem(ICONS[i], nameColor + type.displayName, lore));
        }
    }

    // ── Klick-Handler ──────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        for (int i = 0; i < SLOTS.length; i++) {
            if (slot != SLOTS[i]) continue;
            AbilityType type = ORDER[i];
            Inventory inv = event.getInventory();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean ok = plugin.getAbilityManager().tryUpgrade(player, type);
                Bukkit.getScheduler().runTask(plugin, () -> {
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

    private ItemStack makePane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
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

    public static class GuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
