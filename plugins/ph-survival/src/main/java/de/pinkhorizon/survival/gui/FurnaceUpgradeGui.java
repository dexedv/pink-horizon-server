package de.pinkhorizon.survival.gui;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.FurnaceUpgradeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ofen-Upgrade-GUI mit zwei Sektionen:
 *   ⚡ Geschwindigkeit (Lv 1-10) – schnelleres Schmelzen
 *   🍀 Glück         (Lv 0-10) – Chance auf doppelten Output
 *
 * Layout (54 Slots, 6 Reihen):
 *  Reihe 0: [G][G][SPEED_INFO][G][FURNACE][G][FORTUNE_INFO][G][G]
 *  Reihe 1: [G][G][SPEED_BTN ][G][   G  ][G][FORTUNE_BTN ][G][G]
 *  Reihe 2: Speed  Lv1-Lv9   (Slots 18-26)
 *  Reihe 3: Speed  Lv10       (Slot  27)   + Füller
 *  Reihe 4: Fortune Lv1-Lv9  (Slots 36-44)
 *  Reihe 5: Fortune Lv10      (Slot  45)   + Füller + [CLOSE, Slot 53]
 */
public class FurnaceUpgradeGui implements Listener {

    private static final Component TITLE = Component.text("⚒ Ofen-Upgrade")
        .color(NamedTextColor.GOLD)
        .decorate(TextDecoration.BOLD);

    private static final int SLOT_SPEED_INFO    = 2;
    private static final int SLOT_FURNACE       = 4;
    private static final int SLOT_FORTUNE_INFO  = 6;
    private static final int SLOT_SPEED_BTN     = 11;
    private static final int SLOT_FORTUNE_BTN   = 15;
    private static final int SLOT_CLOSE         = 53;

    private final Map<UUID, Block> openBlocks = new ConcurrentHashMap<>();
    private final PHSurvival plugin;

    public FurnaceUpgradeGui(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Öffnen ────────────────────────────────────────────────────────────

    public void open(Player player, Block furnaceBlock) {
        openBlocks.put(player.getUniqueId(), furnaceBlock);
        if (plugin.getClaimManager().isOwner(furnaceBlock.getChunk(), player.getUniqueId()))
            plugin.getFurnaceUpgradeManager().claimOwnership(furnaceBlock, player.getUniqueId().toString());

        FurnaceUpgradeManager mgr = plugin.getFurnaceUpgradeManager();
        int  speedLv    = mgr.getLevel(furnaceBlock);
        int  fortuneLv  = mgr.getFortuneLevel(furnaceBlock);
        long balance    = plugin.getEconomyManager().getBalance(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Füller
        ItemStack glass = make(Material.GRAY_STAINED_GLASS_PANE, txt(" ", NamedTextColor.GRAY), List.of());
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        // ── Ofen-Status (Mitte oben) ───────────────────────────────────────
        inv.setItem(SLOT_FURNACE, make(Material.FURNACE,
            txt("⚒ Ofen-Status", NamedTextColor.GOLD),
            List.of(
                txt("⚡ Speed:  Lv " + speedLv + " – " + FurnaceUpgradeManager.NAMES[speedLv], NamedTextColor.YELLOW),
                txt("🍀 Glück:  " + (fortuneLv > 0
                    ? "Lv " + fortuneLv + " – " + FurnaceUpgradeManager.FORTUNE_CHANCE_PCT[fortuneLv] + "% Chance"
                    : "Kein Upgrade"), NamedTextColor.GREEN),
                Component.empty(),
                txt("Coins: " + balance, NamedTextColor.AQUA)
            )));

        // ── Geschwindigkeit ────────────────────────────────────────────────
        inv.setItem(SLOT_SPEED_INFO, make(Material.BLAZE_ROD,
            txt("⚡ Geschwindigkeit", NamedTextColor.YELLOW),
            List.of(
                txt("Lv " + speedLv + " – " + FurnaceUpgradeManager.NAMES[speedLv], NamedTextColor.WHITE),
                txt("+" + FurnaceUpgradeManager.SPEED_PCT[speedLv] + "% Speed", NamedTextColor.AQUA),
                txt("Kochzeit: " + cookSec(speedLv) + "s", NamedTextColor.GRAY)
            )));

        if (speedLv >= FurnaceUpgradeManager.MAX_LEVEL) {
            inv.setItem(SLOT_SPEED_BTN, make(Material.NETHER_STAR,
                txt("★ Maximaler Speed!", NamedTextColor.AQUA),
                List.of(txt("Schnellstmöglich – kein Upgrade verfügbar.", NamedTextColor.GRAY))));
        } else {
            int  next       = speedLv + 1;
            long cost       = FurnaceUpgradeManager.COSTS[next];
            boolean afford  = balance >= cost;
            inv.setItem(SLOT_SPEED_BTN, make(
                afford ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                txt("⬆ Speed → Lv " + next + " (" + FurnaceUpgradeManager.NAMES[next] + ")",
                    afford ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(
                    txt("+" + FurnaceUpgradeManager.SPEED_PCT[next] + "% | " + cookSec(next) + "s", NamedTextColor.AQUA),
                    Component.empty(),
                    txt("Kosten: " + cost + " Coins", NamedTextColor.YELLOW),
                    txt("Wallet: " + balance + " Coins", afford ? NamedTextColor.GREEN : NamedTextColor.RED),
                    Component.empty(),
                    afford ? txt("Klicken zum Upgraden", NamedTextColor.GREEN)
                           : txt("Nicht genug Coins!", NamedTextColor.RED)
                )));
        }

        // Speed-Übersicht: Lv1-9 → Slots 18-26, Lv10 → Slot 27
        for (int lvl = 1; lvl <= FurnaceUpgradeManager.MAX_LEVEL; lvl++) {
            int slot = lvl <= 9 ? 18 + lvl - 1 : 27;
            boolean done    = lvl <= speedLv;
            boolean current = lvl == speedLv;
            Material mat = current ? Material.YELLOW_DYE : done ? Material.LIME_DYE : Material.GRAY_DYE;
            List<Component> lore = new ArrayList<>();
            lore.add(txt("+" + FurnaceUpgradeManager.SPEED_PCT[lvl] + "% – " + cookSec(lvl) + "s", NamedTextColor.AQUA));
            if (lvl > 1)
                lore.add(txt("Kosten: " + FurnaceUpgradeManager.COSTS[lvl] + " Coins",
                    done ? NamedTextColor.GRAY : NamedTextColor.YELLOW));
            if (current) { lore.add(Component.empty()); lore.add(txt("◄ Aktuell", NamedTextColor.GREEN)); }
            inv.setItem(slot, make(mat,
                txt("Lv " + lvl + " – " + FurnaceUpgradeManager.NAMES[lvl],
                    current ? NamedTextColor.YELLOW : done ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore));
        }

        // ── Glück (Fortune) ────────────────────────────────────────────────
        inv.setItem(SLOT_FORTUNE_INFO, make(Material.EMERALD,
            txt("🍀 Glück (Doppel-Output)", NamedTextColor.GREEN),
            List.of(
                txt(fortuneLv > 0
                    ? "Lv " + fortuneLv + " – " + FurnaceUpgradeManager.FORTUNE_CHANCE_PCT[fortuneLv] + "% Chance"
                    : "Noch kein Upgrade", NamedTextColor.WHITE),
                txt("Chance auf 2x Items beim Schmelzen", NamedTextColor.GRAY)
            )));

        if (fortuneLv >= FurnaceUpgradeManager.MAX_FORTUNE_LEVEL) {
            inv.setItem(SLOT_FORTUNE_BTN, make(Material.NETHER_STAR,
                txt("★ Maximales Glück!", NamedTextColor.GREEN),
                List.of(txt("50% Chance auf doppelten Output!", NamedTextColor.AQUA))));
        } else {
            int  next       = fortuneLv + 1;
            long cost       = FurnaceUpgradeManager.FORTUNE_COSTS[next];
            boolean afford  = balance >= cost;
            inv.setItem(SLOT_FORTUNE_BTN, make(
                afford ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                txt("⬆ Glück → Lv " + next + " (" + FurnaceUpgradeManager.FORTUNE_CHANCE_PCT[next] + "% Chance)",
                    afford ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(
                    txt("Chance auf 2x Output: " + FurnaceUpgradeManager.FORTUNE_CHANCE_PCT[next] + "%", NamedTextColor.AQUA),
                    Component.empty(),
                    txt("Kosten: " + cost + " Coins", NamedTextColor.YELLOW),
                    txt("Wallet: " + balance + " Coins", afford ? NamedTextColor.GREEN : NamedTextColor.RED),
                    Component.empty(),
                    afford ? txt("Klicken zum Upgraden", NamedTextColor.GREEN)
                           : txt("Nicht genug Coins!", NamedTextColor.RED)
                )));
        }

        // Fortune-Übersicht: Lv1-9 → Slots 36-44, Lv10 → Slot 45
        for (int lvl = 1; lvl <= FurnaceUpgradeManager.MAX_FORTUNE_LEVEL; lvl++) {
            int slot = lvl <= 9 ? 36 + lvl - 1 : 45;
            boolean done    = lvl <= fortuneLv;
            boolean current = lvl == fortuneLv;
            Material mat = current ? Material.YELLOW_DYE : done ? Material.LIME_DYE : Material.GRAY_DYE;
            List<Component> lore = new ArrayList<>();
            lore.add(txt("Chance: " + FurnaceUpgradeManager.FORTUNE_CHANCE_PCT[lvl] + "%", NamedTextColor.AQUA));
            lore.add(txt("Kosten: " + FurnaceUpgradeManager.FORTUNE_COSTS[lvl] + " Coins",
                done ? NamedTextColor.GRAY : NamedTextColor.YELLOW));
            if (current) { lore.add(Component.empty()); lore.add(txt("◄ Aktuell", NamedTextColor.GREEN)); }
            inv.setItem(slot, make(mat,
                txt("Glück Lv " + lvl + " – " + FurnaceUpgradeManager.FORTUNE_CHANCE_PCT[lvl] + "% Chance",
                    current ? NamedTextColor.YELLOW : done ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore));
        }

        // Schließen
        inv.setItem(SLOT_CLOSE, make(Material.BARRIER,
            txt("Schließen", NamedTextColor.RED),
            List.of(txt("GUI schließen", NamedTextColor.GRAY))));

        player.openInventory(inv);
    }

    // ── Click-Handler ─────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openBlocks.containsKey(player.getUniqueId())) return;
        if (!event.getView().title().equals(TITLE)) return;
        event.setCancelled(true);

        Block block = openBlocks.get(player.getUniqueId());
        int slot = event.getRawSlot();

        if (slot == SLOT_CLOSE) { player.closeInventory(); return; }

        if (slot == SLOT_SPEED_BTN) {
            FurnaceUpgradeManager mgr = plugin.getFurnaceUpgradeManager();
            int cur = mgr.getLevel(block);
            if (cur >= FurnaceUpgradeManager.MAX_LEVEL) {
                player.sendActionBar(txt("Maximaler Speed bereits erreicht!", NamedTextColor.RED));
                return;
            }
            long cost = FurnaceUpgradeManager.COSTS[cur + 1];
            if (!plugin.getEconomyManager().has(player.getUniqueId(), cost)) {
                player.sendActionBar(Component.text("Nicht genug Coins! Benötigt: ", NamedTextColor.RED)
                    .append(Component.text(cost + " Coins", NamedTextColor.YELLOW)));
                return;
            }
            if (mgr.tryUpgrade(block, player)) {
                int lv = mgr.getLevel(block);
                player.sendActionBar(Component.text("⚡ Speed Lv ", NamedTextColor.GREEN)
                    .append(Component.text(lv + " – " + FurnaceUpgradeManager.NAMES[lv], NamedTextColor.YELLOW))
                    .append(Component.text(" freigeschaltet!", NamedTextColor.GREEN)));
                player.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, block));
            }
        } else if (slot == SLOT_FORTUNE_BTN) {
            FurnaceUpgradeManager mgr = plugin.getFurnaceUpgradeManager();
            int cur = mgr.getFortuneLevel(block);
            if (cur >= FurnaceUpgradeManager.MAX_FORTUNE_LEVEL) {
                player.sendActionBar(txt("Maximales Glück bereits erreicht!", NamedTextColor.RED));
                return;
            }
            long cost = FurnaceUpgradeManager.FORTUNE_COSTS[cur + 1];
            if (!plugin.getEconomyManager().has(player.getUniqueId(), cost)) {
                player.sendActionBar(Component.text("Nicht genug Coins! Benötigt: ", NamedTextColor.RED)
                    .append(Component.text(cost + " Coins", NamedTextColor.YELLOW)));
                return;
            }
            if (mgr.tryUpgradeFortune(block, player)) {
                int lv = mgr.getFortuneLevel(block);
                player.sendActionBar(Component.text("🍀 Glück Lv ", NamedTextColor.GREEN)
                    .append(Component.text(lv + " – " + FurnaceUpgradeManager.FORTUNE_CHANCE_PCT[lv] + "% Chance", NamedTextColor.YELLOW))
                    .append(Component.text(" freigeschaltet!", NamedTextColor.GREEN)));
                player.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, block));
            }
        }
    }

    // ── Close-Handler ─────────────────────────────────────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().title().equals(TITLE)) return;
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;
        openBlocks.remove(player.getUniqueId());
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private static String cookSec(int level) {
        int ticks = FurnaceUpgradeManager.COOK_TICKS[Math.min(level, FurnaceUpgradeManager.MAX_LEVEL)];
        return String.valueOf(ticks / 20);
    }

    private ItemStack make(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component txt(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }
}
