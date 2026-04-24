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

public class FurnaceUpgradeGui implements Listener {

    // Adventure Component als Titel – direkter equals()-Vergleich, kein Legacy-Parsing
    private static final Component TITLE = Component.text("⚒ Ofen-Upgrade")
        .color(NamedTextColor.GOLD)
        .decorate(TextDecoration.BOLD);

    // Welcher Spieler hat welchen Ofen offen
    private final Map<UUID, Block> openBlocks = new ConcurrentHashMap<>();

    private final PHSurvival plugin;

    public FurnaceUpgradeGui(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Öffnen ────────────────────────────────────────────────────────────

    public void open(Player player, Block furnaceBlock) {
        openBlocks.put(player.getUniqueId(), furnaceBlock);

        FurnaceUpgradeManager mgr = plugin.getFurnaceUpgradeManager();
        int currentLevel = mgr.getLevel(furnaceBlock);
        long balance     = plugin.getEconomyManager().getBalance(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Slot 4: aktueller Level
        inv.setItem(4, make(Material.FURNACE,
            txt("Ofen – Level " + currentLevel + " (" + FurnaceUpgradeManager.NAMES[currentLevel] + ")", NamedTextColor.GOLD),
            buildCurrentLore(currentLevel)));

        // Slot 13: Upgrade-Button oder Max-Level-Anzeige
        if (currentLevel >= FurnaceUpgradeManager.MAX_LEVEL) {
            inv.setItem(13, make(Material.NETHER_STAR,
                txt("★ Maximales Level erreicht!", NamedTextColor.AQUA),
                List.of(
                    txt("Dein Ofen schmilzt so schnell", NamedTextColor.GRAY),
                    txt("wie technisch möglich.", NamedTextColor.GRAY)
                )));
        } else {
            int nextLevel = currentLevel + 1;
            long cost     = FurnaceUpgradeManager.COSTS[nextLevel];
            boolean canAfford = balance >= cost;

            inv.setItem(13, make(
                canAfford ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                txt("Upgrade auf Level " + nextLevel + " – " + FurnaceUpgradeManager.NAMES[nextLevel],
                    canAfford ? NamedTextColor.GREEN : NamedTextColor.RED),
                buildUpgradeLore(currentLevel, nextLevel, cost, balance, canAfford)
            ));
        }

        // Slots 18–22: Level-Übersicht
        for (int lvl = 1; lvl <= FurnaceUpgradeManager.MAX_LEVEL; lvl++) {
            int slot = 18 + (lvl - 1);
            boolean done    = lvl <= currentLevel;
            boolean current = lvl == currentLevel;
            Material mat = done ? Material.LIME_DYE : Material.GRAY_DYE;
            if (current) mat = Material.YELLOW_DYE;

            List<Component> lore = new ArrayList<>();
            lore.add(txt("Schmelzzeit: " + cookSeconds(lvl), NamedTextColor.WHITE));
            lore.add(txt("Geschwindigkeit: +" + FurnaceUpgradeManager.SPEED_PCT[lvl] + "%", NamedTextColor.AQUA));
            if (lvl > 1)
                lore.add(txt("Kosten: " + FurnaceUpgradeManager.COSTS[lvl] + " Coins",
                    done ? NamedTextColor.GRAY : NamedTextColor.YELLOW));
            if (current) {
                lore.add(Component.empty());
                lore.add(txt("◄ Aktuelles Level", NamedTextColor.GREEN));
            }

            inv.setItem(slot, make(mat,
                txt("Level " + lvl + " – " + FurnaceUpgradeManager.NAMES[lvl],
                    current ? NamedTextColor.YELLOW : done ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore));
        }

        // Slot 26: Schließen
        inv.setItem(26, make(Material.BARRIER,
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

        Block furnaceBlock = openBlocks.get(player.getUniqueId());
        int slot = event.getRawSlot();

        if (slot == 26) { player.closeInventory(); return; }

        if (slot == 13) {
            FurnaceUpgradeManager mgr = plugin.getFurnaceUpgradeManager();
            int current = mgr.getLevel(furnaceBlock);
            if (current >= FurnaceUpgradeManager.MAX_LEVEL) {
                player.sendActionBar(txt("Maximales Level bereits erreicht!", NamedTextColor.RED));
                return;
            }
            int nextLevel = current + 1;
            long cost     = FurnaceUpgradeManager.COSTS[nextLevel];

            if (!plugin.getEconomyManager().has(player.getUniqueId(), cost)) {
                player.sendActionBar(Component.text("Nicht genug Coins! Benoetigt: ", NamedTextColor.RED)
                    .append(Component.text(cost + " Coins", NamedTextColor.YELLOW)));
                return;
            }

            if (mgr.tryUpgrade(furnaceBlock, player)) {
                int newLevel = mgr.getLevel(furnaceBlock);
                player.sendActionBar(Component.text("Ofen auf Level ", NamedTextColor.GREEN)
                    .append(Component.text(String.valueOf(newLevel), NamedTextColor.YELLOW))
                    .append(Component.text(" (" + FurnaceUpgradeManager.NAMES[newLevel] + ") upgegradet!", NamedTextColor.GREEN)));
                player.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, furnaceBlock));
            }
        }
    }

    // ── Close-Handler ─────────────────────────────────────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().title().equals(TITLE)) return;
        openBlocks.remove(player.getUniqueId());
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private List<Component> buildCurrentLore(int lvl) {
        return List.of(
            txt("Schmelzzeit: " + cookSeconds(lvl) + " Sekunden", NamedTextColor.WHITE),
            txt("Geschwindigkeit: +" + FurnaceUpgradeManager.SPEED_PCT[lvl] + "%", NamedTextColor.AQUA),
            Component.empty(),
            txt("Shift + Rechtsklick auf den Ofen", NamedTextColor.DARK_GRAY),
            txt("um dieses Menu zu oeffnen.", NamedTextColor.DARK_GRAY)
        );
    }

    private List<Component> buildUpgradeLore(int cur, int next, long cost, long balance, boolean canAfford) {
        return List.of(
            txt("Aktuell: " + cookSeconds(cur) + "s  Neu: " + cookSeconds(next) + "s", NamedTextColor.GRAY),
            txt("Beschleunigung: +" + FurnaceUpgradeManager.SPEED_PCT[next] + "%", NamedTextColor.AQUA),
            Component.empty(),
            txt("Kosten:  " + cost + " Coins", NamedTextColor.YELLOW),
            txt("Wallet:  " + balance + " Coins", canAfford ? NamedTextColor.GREEN : NamedTextColor.RED),
            Component.empty(),
            canAfford ? txt("Klicken zum Upgraden", NamedTextColor.GREEN)
                      : txt("Nicht genug Coins!", NamedTextColor.RED)
        );
    }

    private static String cookSeconds(int level) {
        int ticks = FurnaceUpgradeManager.MAX_LEVEL >= level
            ? new int[]{ 0, 200, 150, 100, 60, 30 }[level] : 200;
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
