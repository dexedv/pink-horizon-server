package de.pinkhorizon.survival.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TutorialGui implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** UUID → aktuell geöffnete Seite ("main" | "allgemein" | …) */
    private final Map<UUID, String> openMenus = new HashMap<>();

    // ── Öffentliche API ───────────────────────────────────────────────────

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            MM.deserialize("<bold><gradient:#FF69B4:#9B59B6>✦ Pink Horizon – Tutorial ✦</gradient></bold>"));

        fill(inv, gl());

        inv.setItem(20, cat(Material.COMPASS,       "<gold><bold>⚙ Allgemein</bold></gold>",        "/spawn, /rtp, /kit, /stats …"));
        inv.setItem(22, cat(Material.IRON_SWORD,    "<green><bold>🏗 Claims</bold></green>",         "/claim, /trust, /unclaim …"));
        inv.setItem(24, cat(Material.ORANGE_BED,    "<yellow><bold>🏠 Homes</bold></yellow>",        "/sethome, /home, /homes …"));
        inv.setItem(29, cat(Material.ENDER_PEARL,   "<aqua><bold>🔀 Teleport</bold></aqua>",        "/tpa, /tpaccept, /back …"));
        inv.setItem(31, cat(Material.GOLD_INGOT,    "<gold><bold>💰 Wirtschaft</bold></gold>",      "/balance, /pay, /shop, /jobs …"));
        inv.setItem(33, cat(Material.WRITABLE_BOOK, "<light_purple><bold>💬 Soziales</bold></light_purple>", "/friend, /mail, /trade …"));

        openMenus.put(player.getUniqueId(), "main");
        player.openInventory(inv);
    }

    // ── Sub-Menüs ─────────────────────────────────────────────────────────

    private void openAllgemein(Player player) {
        Inventory inv = sub("<gold><bold>⚙ Allgemein</bold></gold>");
        inv.setItem(10, cmd(Material.COMPASS,        "/spawn",          "Zum Spawn teleportieren."));
        inv.setItem(11, cmd(Material.ENDER_PEARL,    "/rtp",            "Zufällig in der Welt teleportieren."));
        inv.setItem(12, cmd(Material.CHEST,          "/kit starter",    "Starter-Kit erhalten (24h Cooldown)."));
        inv.setItem(13, cmd(Material.BOOK,           "/stats [Spieler]","Statistiken anzeigen."));
        inv.setItem(14, cmd(Material.GOLD_BLOCK,     "/lb [Typ]",       "Bestenliste: coins | kills | playtime"));
        inv.setItem(15, cmd(Material.NETHER_STAR,    "/achievements",   "Deine Errungenschaften anzeigen."));
        inv.setItem(16, cmd(Material.PAPER,          "/quests",         "Tägliche Quests anzeigen."));
        inv.setItem(19, cmd(Material.CLOCK,          "/playtime",       "Deine Spielzeit anzeigen."));
        inv.setItem(20, cmd(Material.FEATHER,        "/back",           "Zum letzten Sterbeort teleportieren."));
        back(inv);
        openMenus.put(player.getUniqueId(), "allgemein");
        player.openInventory(inv);
    }

    private void openClaims(Player player) {
        Inventory inv = sub("<green><bold>🏗 Claims</bold></green>");
        inv.setItem(10, cmd(Material.IRON_SWORD,     "/claim",          "Aktuellen Chunk schützen (kostet Coins)."));
        inv.setItem(11, cmd(Material.SHEARS,         "/unclaim",        "Chunk-Schutz entfernen."));
        inv.setItem(12, cmd(Material.MAP,            "/claimlist",      "Alle deine Claims auflisten."));
        inv.setItem(13, cmd(Material.EMERALD,        "/trust <Spieler>","Spieler in deinem Claim erlauben."));
        inv.setItem(14, cmd(Material.REDSTONE,       "/untrust <Spieler>","Vertrauen entziehen."));
        inv.setItem(15, cmd(Material.BOOK,           "/trustlist",      "Vertrauensliste anzeigen."));
        inv.setItem(19, cmd(Material.GOLD_NUGGET,    "Preis",           "Jeder Chunk-Claim kostet Coins.", "Sieh dein Guthaben mit /balance."));
        inv.setItem(20, cmd(Material.GRASS_BLOCK,    "Was ist ein Chunk?","16x16 Blöcke großer Bereich.", "Sichtbar mit F3+G."));
        back(inv);
        openMenus.put(player.getUniqueId(), "claims");
        player.openInventory(inv);
    }

    private void openHomes(Player player) {
        Inventory inv = sub("<yellow><bold>🏠 Homes</bold></yellow>");
        inv.setItem(10, cmd(Material.ORANGE_BED,     "/sethome [Name]", "Home an aktueller Position speichern.", "Max. 3 Homes pro Spieler."));
        inv.setItem(11, cmd(Material.ENDER_PEARL,    "/home [Name]",    "Zu einem deiner Homes teleportieren."));
        inv.setItem(12, cmd(Material.BOOK,           "/homes",          "Alle gesetzten Homes anzeigen."));
        inv.setItem(13, cmd(Material.SHEARS,         "/delhome [Name]", "Einen Home löschen."));
        back(inv);
        openMenus.put(player.getUniqueId(), "homes");
        player.openInventory(inv);
    }

    private void openTeleport(Player player) {
        Inventory inv = sub("<aqua><bold>🔀 Teleport</bold></aqua>");
        inv.setItem(10, cmd(Material.ENDER_EYE,      "/tpa <Spieler>",  "Teleportanfrage an einen Spieler senden."));
        inv.setItem(11, cmd(Material.LIME_DYE,       "/tpaccept",       "Eingehende Teleportanfrage annehmen."));
        inv.setItem(12, cmd(Material.RED_DYE,        "/tpdeny",         "Teleportanfrage ablehnen."));
        inv.setItem(13, cmd(Material.FEATHER,        "/back",           "Zum letzten Sterbe- oder Teleport-Ort."));
        inv.setItem(14, cmd(Material.MAP,            "/warp <Name>",    "Zu einem öffentlichen Warp-Punkt."));
        inv.setItem(15, cmd(Material.BOOK,           "/warps",          "Alle verfügbaren Warps auflisten."));
        back(inv);
        openMenus.put(player.getUniqueId(), "teleport");
        player.openInventory(inv);
    }

    private void openWirtschaft(Player player) {
        Inventory inv = sub("<gold><bold>💰 Wirtschaft</bold></gold>");
        inv.setItem(10, cmd(Material.GOLD_INGOT,     "/balance",        "Deinen Kontostand anzeigen."));
        inv.setItem(11, cmd(Material.EMERALD,        "/pay <Sp.> <Bet.>","Coins an einen Spieler überweisen."));
        inv.setItem(12, cmd(Material.GOLD_BLOCK,     "/baltop",         "Die reichsten Spieler anzeigen."));
        inv.setItem(13, cmd(Material.CHEST,          "/shop",           "Server-Shop öffnen (Upgrades & Items)."));
        inv.setItem(14, cmd(Material.DIAMOND,        "/sell <hand|all>","Items aus der Hand oder Inv verkaufen."));
        inv.setItem(15, cmd(Material.IRON_PICKAXE,   "/jobs",           "Job wählen und Geld beim Spielen verdienen."));
        inv.setItem(19, cmd(Material.BARREL,         "/bank",           "Bank-Konto verwalten."));
        inv.setItem(20, cmd(Material.PAPER,          "/bank deposit <B>","Coins einzahlen."));
        inv.setItem(21, cmd(Material.PAPER,          "/bank withdraw <B>","Coins abheben."));
        back(inv);
        openMenus.put(player.getUniqueId(), "wirtschaft");
        player.openInventory(inv);
    }

    private void openSoziales(Player player) {
        Inventory inv = sub("<light_purple><bold>💬 Soziales</bold></light_purple>");
        inv.setItem(10, cmd(Material.WRITABLE_BOOK,  "/friend add <Sp.>","Freundschaftsanfrage senden."));
        inv.setItem(11, cmd(Material.BOOK,           "/friend list",    "Deine Freundesliste anzeigen."));
        inv.setItem(12, cmd(Material.FEATHER,        "/mail send <Sp.> <Msg>","Nachricht an Spieler senden (auch offline)."));
        inv.setItem(13, cmd(Material.PAPER,          "/mail read",      "Eingegangene Nachrichten lesen."));
        inv.setItem(14, cmd(Material.EMERALD,        "/trade <Spieler>","Sicheren Handel mit Spieler starten."));
        inv.setItem(15, cmd(Material.REDSTONE,       "/report <Sp.> <Gr.>","Regelverstoß melden."));
        inv.setItem(19, cmd(Material.IRON_DOOR,      "/hub",            "Zur Lobby zurückkehren."));
        back(inv);
        openMenus.put(player.getUniqueId(), "soziales");
        player.openInventory(inv);
    }

    // ── Events ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (openMenus.containsKey(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String page = openMenus.get(player.getUniqueId());
        if (page == null) return;

        // Immer canceln – kein Item darf sich bewegen
        event.setCancelled(true);

        // Nur Klicks im GUI-Bereich (rawSlot < Größe des oberen Inventars) verarbeiten
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();
        if (page.equals("main")) {
            switch (slot) {
                case 20 -> openAllgemein(player);
                case 22 -> openClaims(player);
                case 24 -> openHomes(player);
                case 29 -> openTeleport(player);
                case 31 -> openWirtschaft(player);
                case 33 -> openSoziales(player);
            }
        } else if (slot == 49) {
            openMain(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────

    private Inventory sub(String title) {
        Inventory inv = Bukkit.createInventory(null, 54, MM.deserialize(title));
        fill(inv, gl());
        return inv;
    }

    /** Kategorie-Item (Hauptmenü) */
    private ItemStack cat(Material mat, String name, String preview) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(List.of(
            MM.deserialize("<gray>" + preview),
            Component.empty(),
            MM.deserialize("<yellow>» Klicken für Details")
        ));
        item.setItemMeta(meta);
        return item;
    }

    /** Befehl-Item (Sub-Menü) */
    private ItemStack cmd(Material mat, String cmdName, String... descLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<white><bold>" + cmdName + "</bold></white>"));
        List<Component> lore = new ArrayList<>();
        for (String line : descLines)
            lore.add(MM.deserialize("<gray>" + line));
        item.setItemMeta(meta);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Zurück-Button */
    private void back(Inventory inv) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<red>← Zurück"));
        item.setItemMeta(meta);
        inv.setItem(49, item);
    }

    /** Grauer Glasrahmen */
    private ItemStack gl() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    /** Füllt Rand + leere Slots mit dem gegebenen Item */
    private void fill(Inventory inv, ItemStack filler) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }
}
