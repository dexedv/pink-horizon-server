package de.pinkhorizon.smash.listeners;

import de.pinkhorizon.smash.PHSmash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class SmashNavigatorListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String COMPASS_NAME = "§c§lNavigator";

    private final PHSmash plugin;

    public SmashNavigatorListener(PHSmash plugin) {
        this.plugin = plugin;
    }

    // ── Compass-Item-Fabrik (statisch, damit ArenaManager es nutzen kann) ──

    public static ItemStack buildCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(COMPASS_NAME));
        meta.lore(List.of(
            LEGACY.deserialize("§7Rechtsklick §8→ §7Menü öffnen")));
        item.setItemMeta(meta);
        return item;
    }

    // ── Rechtsklick mit Kompass ────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack item   = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.COMPASS || !isNavigator(item)) return;

        event.setCancelled(true);
        openNavGui(player);
    }

    private boolean isNavigator(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        Component name = item.getItemMeta().displayName();
        return name != null && LEGACY.serialize(name).contains("Navigator");
    }

    // ── Navigator-GUI ──────────────────────────────────────────────────────

    private void openNavGui(Player player) {
        Inventory inv = Bukkit.createInventory(new NavHolder(), 54,
            LEGACY.deserialize("§8§l≡ §r§c§lSmash §8– §7Menü"));
        fillNav(inv, player);
        player.openInventory(inv);
    }

    private void fillNav(Inventory inv, Player player) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  pm   = pane.getItemMeta();
        pm.displayName(Component.empty());
        pane.setItemMeta(pm);
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        boolean inArena = plugin.getArenaManager().hasArena(player.getUniqueId());

        // ── Reihe 2: Kern-Aktionen ─────────────────────────────────────────
        // Slot 10 – Arena betreten
        inv.setItem(10, nav(
            inArena ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
            "§a§l▶ Arena betreten",
            List.of("§7Starte deine private Arena",
                    inArena ? "§c✗ Du bist bereits in einer Arena" : "§a▶ Klicken zum Betreten")));

        // Slot 12 – Arena verlassen
        inv.setItem(12, nav(
            inArena ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            "§c§l✖ Arena verlassen",
            List.of("§7Kehre zur Lobby zurück",
                    inArena ? "§a▶ Klicken zum Verlassen" : "§c✗ Du bist in keiner Arena")));

        // Slot 14 – Item-Upgrades
        inv.setItem(14, nav(Material.DIAMOND,
            "§b§l⬆ Item-Upgrades",
            List.of("§7Verbessere Angriff, Verteidigung,", "§7Gesundheit & mehr", "§a▶ Klicken")));

        // Slot 16 – Fähigkeiten
        inv.setItem(16, nav(Material.NETHER_STAR,
            "§6§l★ Fähigkeiten",
            List.of("§7Freischalten mit Münzen", "§a▶ Klicken")));

        // ── Reihe 4: Erweiterte Systeme ───────────────────────────────────
        // Slot 28 – Shop
        inv.setItem(28, nav(Material.EMERALD,
            "§a§l☆ Shop",
            List.of("§7Kaufe Tränke & Items", "§a▶ Klicken")));

        // Slot 30 – Talente
        inv.setItem(30, nav(Material.ENCHANTED_BOOK,
            "§5§l✦ Talente",
            List.of("§7Passive Boss-Kern-Upgrades", "§a▶ Klicken")));

        // Slot 32 – Runen
        inv.setItem(32, nav(Material.BLAZE_POWDER,
            "§c§l⚡ Runen",
            List.of("§7Zeitlich begrenzte Boni", "§7(Krieg / Schutz / Glück)", "§a▶ Klicken")));

        // Slot 34 – Tägliche Herausforderungen
        inv.setItem(34, nav(Material.CLOCK,
            "§e§l⏳ Tägliche Quests",
            List.of("§73 neue Herausforderungen täglich", "§a▶ Klicken")));

        // ── Reihe 6: Statistiken ──────────────────────────────────────────
        // Slot 49 – Statistiken (Mitte unten)
        inv.setItem(49, nav(Material.PAPER,
            "§e§l✦ Statistiken",
            List.of("§7Deine persönlichen Stats", "§a▶ Klicken")));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof NavHolder)) return;
        event.setCancelled(true);

        boolean inArena = plugin.getArenaManager().hasArena(player.getUniqueId());

        switch (event.getRawSlot()) {
            case 10 -> {
                player.closeInventory();
                if (!inArena) plugin.getArenaManager().createArena(player);
                else player.sendMessage("§eDu bist bereits in einer Arena! §7(/stb leave)");
            }
            case 12 -> {
                player.closeInventory();
                if (inArena) plugin.getArenaManager().destroyArena(player.getUniqueId());
                else player.sendMessage("§eDu bist in keiner Arena!");
            }
            case 14 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getUpgradeGui().open(player), 1L);
            }
            case 16 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getAbilityGui().open(player), 1L);
            }
            case 28 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getShopGui().open(player), 1L);
            }
            case 30 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getTalentGui().open(player), 1L);
            }
            case 32 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getRuneGui().open(player), 1L);
            }
            case 34 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getDailyChallengeGui().open(player), 1L);
            }
            case 49 -> {
                player.closeInventory();
                showStats(player);
            }
        }
    }

    private void showStats(Player player) {
        int  kills      = plugin.getPlayerDataManager().getKills(player.getUniqueId());
        long damage     = plugin.getPlayerDataManager().getTotalDamage(player.getUniqueId());
        int  bossLevel  = plugin.getPlayerDataManager().getPersonalBossLevel(player.getUniqueId());
        long coins      = plugin.getCoinManager().getCoins(player.getUniqueId());
        player.sendMessage("§c§l⚔ Deine Stats:");
        player.sendMessage("§7  Boss-Level:     §c" + bossLevel);
        player.sendMessage("§7  Boss-Kills:     §a" + kills);
        player.sendMessage("§7  Gesamt-Schaden: §e" + damage);
        player.sendMessage("§7  Münzen:         §e" + coins);
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private ItemStack nav(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        meta.lore(lore.stream().map(LEGACY::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static class NavHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
