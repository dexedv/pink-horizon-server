package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.MoneyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Kompass-Navigator: Rechtsklick auf Kompass in Slot 8 öffnet Menü-Übersicht.
 */
public class NavigatorGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "✦ IdleForge – Menüs";
    private static final int COMPASS_SLOT = 8;

    public NavigatorGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    // ── Kompass geben ────────────────────────────────────────────────────────

    public void giveCompass(Player player) {
        ItemStack compass = buildCompass();
        player.getInventory().setItem(COMPASS_SLOT, compass);
    }

    private ItemStack buildCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<light_purple><bold>✦ Menü-Navigator</bold>"));
        meta.lore(List.of(MM.deserialize("<gray>Rechtsklick → Alle Menüs")));
        item.setItemMeta(meta);
        return item;
    }

    // ── Kompass-Klick ────────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isCompass(item)) return;

        // Rechtsklick auf Block soll kein Block-Event auslösen
        event.setCancelled(true);
        open(player);
    }

    // ── Navigator-GUI ────────────────────────────────────────────────────────

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, MM.deserialize("<light_purple>" + TITLE));

        // Füller
        ItemStack filler = filler();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Stats-Item oben
        inv.setItem(4, buildStatsItem(data));

        // Menü-Buttons
        inv.setItem(10, buildButton(Material.GOLD_BLOCK,      "<gold>Generator-Shop",     "<gray>Generatoren kaufen",           "/gen shop"));
        inv.setItem(12, buildButton(Material.ANVIL,           "<aqua>Upgrades",            "<gray>Generatoren upgraden",         "/gen upgrade"));
        inv.setItem(14, buildButton(Material.CHEST,           "<yellow>Block-Shop",        "<gray>Inselblöcke kaufen",           "/gen blockshop"));
        inv.setItem(16, buildButton(Material.NETHER_STAR,     "<light_purple>Prestige",    "<gray>Prestige durchführen",         "/gen prestige"));
        inv.setItem(28, buildButton(Material.BOOK,            "<green>Quests",             "<gray>Tägliche Aufgaben",            "/gen quests"));
        inv.setItem(30, buildButton(Material.TOTEM_OF_UNDYING,"<yellow>Achievements",      "<gray>Errungenschaften ansehen",     "/gen achievements"));
        inv.setItem(32, buildButton(Material.DIAMOND,         "<aqua>Leaderboard",         "<gray>Top 10 Spieler",              "/gen top"));
        inv.setItem(34, buildButton(Material.SHIELD,          "<blue>Gilde",               "<gray>Gilde verwalten",             "/gen guild"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(MM.deserialize("<light_purple>" + TITLE))) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return;

        // Letzte Lore-Zeile enthält den Command
        var lore = item.getItemMeta().lore();
        if (lore == null || lore.isEmpty()) return;

        String cmd = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(lore.get(lore.size() - 1));

        if (!cmd.startsWith("/")) return;

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () ->
                player.performCommand(cmd.substring(1)));
    }

    // ── Item-Builder ─────────────────────────────────────────────────────────

    private ItemStack buildButton(Material mat, String name, String desc, String command) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(List.of(
                MM.deserialize(desc),
                MM.deserialize(""),
                MM.deserialize("<yellow>Linksklick → Öffnen"),
                MM.deserialize("<dark_gray>" + command)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildStatsItem(PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold><bold>Deine Stats</bold>"));
        if (data != null) {
            meta.lore(List.of(
                    MM.deserialize("<gray>Guthaben: <green>$" + MoneyManager.formatMoney(data.getMoney())),
                    MM.deserialize("<gray>Prestige: <light_purple>" + data.getPrestige()),
                    MM.deserialize("<gray>Generatoren: <white>" + data.getGenerators().size()),
                    MM.deserialize("<gray>Max Level: <aqua>" + data.maxGeneratorLevel())
            ));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gray> "));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());
        return name.contains("Navigator");
    }
}
