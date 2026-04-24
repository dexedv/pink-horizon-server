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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FurnaceUpgradeGui implements Listener {

    private static final String TITLE_PREFIX = "§6§l⚒ Ofen-Upgrade";

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

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_PREFIX);

        // ── Zeile 1: aktuelle Level-Info ─────────────────────────────────
        // Slot 4: aktueller Level
        inv.setItem(4, make(Material.FURNACE,
            txt("Ofen – Level " + currentLevel + " §7(" + FurnaceUpgradeManager.NAMES[currentLevel] + ")", NamedTextColor.GOLD),
            buildCurrentLore(currentLevel)));

        // ── Zeile 2: Upgrade-Button oder Max-Level-Anzeige ───────────────
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
                txt("➜ Upgrade auf Level " + nextLevel + " – " + FurnaceUpgradeManager.NAMES[nextLevel],
                    canAfford ? NamedTextColor.GREEN : NamedTextColor.RED),
                buildUpgradeLore(currentLevel, nextLevel, cost, balance, canAfford)
            ));
        }

        // ── Zeile 2: Alle Level-Übersicht (Slots 18–22) ──────────────────
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
            if (current)
                lore.add(Component.empty());
            if (current)
                lore.add(txt("◄ Aktuelles Level", NamedTextColor.GREEN));

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
        if (!event.getView().title().equals(Component.text(TITLE_PREFIX))) return;

        // Prüfen ob Titel stimmt (Fallback über String-Vergleich)
        String titleStr = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(event.getView().title());
        if (!titleStr.startsWith("§6§l⚒ Ofen-Upgrade")) return;

        event.setCancelled(true);

        Block furnaceBlock = openBlocks.get(player.getUniqueId());
        if (furnaceBlock == null) { player.closeInventory(); return; }

        int slot = event.getRawSlot();

        // Schließen-Button
        if (slot == 26) { player.closeInventory(); return; }

        // Upgrade-Button
        if (slot == 13) {
            FurnaceUpgradeManager mgr = plugin.getFurnaceUpgradeManager();
            int current = mgr.getLevel(furnaceBlock);
            if (current >= FurnaceUpgradeManager.MAX_LEVEL) {
                player.sendActionBar(Component.text("§cMaximales Level bereits erreicht!"));
                return;
            }
            int nextLevel = current + 1;
            long cost     = FurnaceUpgradeManager.COSTS[nextLevel];

            if (!plugin.getEconomyManager().has(player.getUniqueId(), cost)) {
                player.sendActionBar(Component.text(
                    "§cNicht genug Coins! Benötigt: §e" + cost + " §cCoins."));
                return;
            }

            if (mgr.tryUpgrade(furnaceBlock, player)) {
                player.sendActionBar(Component.text(
                    "§a✔ Ofen auf Level §e" + mgr.getLevel(furnaceBlock)
                    + " §a(" + FurnaceUpgradeManager.NAMES[mgr.getLevel(furnaceBlock)] + "§a) §aupgegradet!"));
                player.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                // GUI aktualisieren
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, furnaceBlock));
            }
        }
    }

    // Cleanup wenn GUI geschlossen wird
    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String titleStr = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(event.getView().title());
        if (titleStr.startsWith("§6§l⚒ Ofen-Upgrade")) {
            openBlocks.remove(player.getUniqueId());
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private List<Component> buildCurrentLore(int lvl) {
        List<Component> l = new ArrayList<>();
        l.add(txt("Schmelzzeit: " + cookSeconds(lvl) + " Sekunden", NamedTextColor.WHITE));
        l.add(txt("Geschwindigkeit: +" + FurnaceUpgradeManager.SPEED_PCT[lvl] + "%", NamedTextColor.AQUA));
        l.add(Component.empty());
        l.add(txt("Shift + Rechtsklick auf den Ofen", NamedTextColor.DARK_GRAY));
        l.add(txt("um dieses Menü zu öffnen.", NamedTextColor.DARK_GRAY));
        return l;
    }

    private List<Component> buildUpgradeLore(int cur, int next, long cost, long balance, boolean canAfford) {
        List<Component> l = new ArrayList<>();
        l.add(txt("Aktuell:  " + cookSeconds(cur) + "s → Neu: " + cookSeconds(next) + "s", NamedTextColor.GRAY));
        l.add(txt("Beschleunigung: +" + FurnaceUpgradeManager.SPEED_PCT[next] + "%", NamedTextColor.AQUA));
        l.add(Component.empty());
        l.add(txt("Kosten:  " + cost + " Coins", NamedTextColor.YELLOW));
        l.add(txt("Wallet:  " + balance + " Coins",
            canAfford ? NamedTextColor.GREEN : NamedTextColor.RED));
        l.add(Component.empty());
        l.add(canAfford
            ? txt("▶ Klicken zum Upgraden", NamedTextColor.GREEN)
            : txt("✗ Nicht genug Coins!", NamedTextColor.RED));
        return l;
    }

    private static String cookSeconds(int level) {
        int ticks = de.pinkhorizon.survival.managers.FurnaceUpgradeManager.MAX_LEVEL >= level
            ? new int[]{ 0, 200, 150, 100, 60, 30 }[level]
            : 200;
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
