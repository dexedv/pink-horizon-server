package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.MoneyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Navigator (Spielerkopf, Slot 8) + Generator-Aufheber (Goldspitzhacke, Slot 7).
 * Beide Items sind nicht wegwerfbar und nicht verschiebbar.
 */
public class NavigatorGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "✦ IdleForge – Menüs";
    private static final int NAVIGATOR_SLOT = 8;
    private static final int PICKER_SLOT    = 7;

    public NavigatorGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    // ── Items geben ──────────────────────────────────────────────────────────

    /** Gibt Navigator-Kopf und Aufheber-Spitzhacke in den Hotbar. */
    public void giveCompass(Player player) {
        player.getInventory().setItem(NAVIGATOR_SLOT, buildNavigator(player));
        player.getInventory().setItem(PICKER_SLOT,    buildPicker());
    }

    private ItemStack buildNavigator(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(MM.deserialize("<light_purple><bold>✦ Menü-Navigator</bold>"));
        meta.lore(List.of(MM.deserialize("<gray>Rechtsklick → Alle Menüs")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPicker() {
        ItemStack item = new ItemStack(Material.GOLDEN_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<yellow><bold>⛏ Generator-Aufheber</bold>"));
        meta.lore(List.of(
                MM.deserialize("<gray>Rechtsklick auf Generator → Aufheben"),
                MM.deserialize("<dark_gray>Level-Fortschritt bleibt erhalten!")
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ── Interaktion ──────────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Navigator → Menü öffnen
        if (isNavigator(item)) {
            if (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            event.setCancelled(true);
            open(player);
            return;
        }

        // Aufheber → Generator aufnehmen
        if (isPicker(item)) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (event.getClickedBlock() == null) return;

            PlacedGenerator gen = plugin.getGeneratorManager()
                    .getAt(event.getClickedBlock().getLocation());
            if (gen == null) return;

            event.setCancelled(true);

            if (!gen.getOwnerUUID().equals(player.getUniqueId())
                    && !player.hasPermission("ph.generators.admin")) {
                player.sendMessage(MM.deserialize("<red>Das ist nicht dein Generator!"));
                return;
            }

            ItemStack drop = plugin.getGeneratorManager().removeGeneratorWithDrop(
                    event.getClickedBlock().getLocation(), player.getUniqueId());
            if (drop == null) return;

            event.getClickedBlock().setType(Material.AIR);

            var leftover = player.getInventory().addItem(drop);
            if (!leftover.isEmpty()) {
                event.getClickedBlock().getWorld().dropItemNaturally(
                        player.getLocation(), leftover.values().iterator().next());
            }
            player.sendMessage(MM.deserialize(
                    "<yellow>⛏ Generator aufgehoben. <gray>Level-Fortschritt bleibt erhalten!"));
        }
    }

    // ── Schutz: nicht wegwerfen ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isSpecialItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (isSpecialItem(event.getMainHandItem()) || isSpecialItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /** Verhindert Verschieben der Special-Items im Inventar. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Cursor hält Special-Item → nicht ablegen
        if (isSpecialItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        // Klick direkt auf Special-Item → nicht nehmen / verschieben
        if (isSpecialItem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }

        // Spezial-Slots im eigenen Inventar schützen (nichts dort ablegen)
        if (event.getClickedInventory() != null
                && event.getClickedInventory() == player.getInventory()) {
            int slot = event.getSlot();
            if (slot == NAVIGATOR_SLOT || slot == PICKER_SLOT) {
                event.setCancelled(true);
            }
        }
    }

    /** Items nach dem Tod wiederherstellen. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> giveCompass(player), 1L);
    }

    // ── Navigator-GUI ────────────────────────────────────────────────────────

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, MM.deserialize("<light_purple>" + TITLE));

        ItemStack filler = filler();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(4, buildStatsItem(data));

        inv.setItem(10, buildButton(Material.GOLD_BLOCK,       "<gold>Generator-Shop",      "<gray>Generatoren kaufen",          "/gen shop"));
        inv.setItem(12, buildButton(Material.ANVIL,            "<aqua>Upgrades",             "<gray>Generatoren upgraden",        "/gen upgrade"));
        inv.setItem(14, buildButton(Material.CHEST,            "<yellow>Block-Shop",         "<gray>Inselblöcke kaufen",          "/gen blockshop"));
        inv.setItem(16, buildButton(Material.NETHER_STAR,      "<light_purple>Prestige",     "<gray>Prestige durchführen",        "/gen prestige"));
        inv.setItem(28, buildButton(Material.BOOK,             "<green>Quests",              "<gray>Tägliche Aufgaben",           "/gen quests"));
        inv.setItem(30, buildButton(Material.TOTEM_OF_UNDYING, "<yellow>Achievements",       "<gray>Errungenschaften ansehen",    "/gen achievements"));
        inv.setItem(32, buildButton(Material.DIAMOND,          "<aqua>Leaderboard",          "<gray>Top 10 Spieler",             "/gen top"));
        inv.setItem(34, buildButton(Material.SHIELD,           "<blue>Gilde",                "<gray>Gilde verwalten",            "/gen guild"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(MM.deserialize("<light_purple>" + TITLE))) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return;

        var lore = item.getItemMeta().lore();
        if (lore == null || lore.isEmpty()) return;

        String cmd = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(lore.get(lore.size() - 1));

        if (!cmd.startsWith("/")) return;

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(cmd.substring(1)));
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

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private boolean isNavigator(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());
        return name.contains("Navigator");
    }

    private boolean isPicker(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_PICKAXE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());
        return name.contains("Aufheber");
    }

    private boolean isSpecialItem(ItemStack item) {
        return isNavigator(item) || isPicker(item);
    }
}
