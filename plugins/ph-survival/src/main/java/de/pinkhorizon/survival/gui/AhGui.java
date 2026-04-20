package de.pinkhorizon.survival.gui;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.AuctionListing;
import de.pinkhorizon.survival.managers.AuctionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AhGui implements Listener {

    private static final int  PAGE_SIZE     = 45;
    private static final String T_BROWSE   = "§6Auktionshaus";
    private static final String T_MINE     = "§6Meine Angebote";
    private static final String T_CONFIRM  = "§6Kauf bestätigen";

    private final PHSurvival     plugin;
    private final AuctionManager auction;

    // Player-State
    private final Map<UUID, Integer>         browsePage     = new HashMap<>();
    private final Map<UUID, Integer>         minePage       = new HashMap<>();
    private final Map<UUID, Map<Integer, UUID>> slotMap     = new HashMap<>();
    private final Map<UUID, UUID>            confirmPending = new HashMap<>();
    private final Map<UUID, String>          viewState      = new HashMap<>();

    public AhGui(PHSurvival plugin) {
        this.plugin  = plugin;
        this.auction = plugin.getAuctionManager();
    }

    // ── Öffnen ───────────────────────────────────────────────────────────

    public void openBrowse(Player player, int page) {
        List<AuctionListing> all = new ArrayList<>(auction.getListings());
        int total = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        page = Math.clamp(page, 0, total - 1);

        Inventory inv    = Bukkit.createInventory(null, 54, T_BROWSE);
        Map<Integer, UUID> sm = new HashMap<>();

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < all.size(); i++) {
            AuctionListing l = all.get(start + i);
            inv.setItem(i, buildListingItem(l,
                    Component.text("Linksklick zum Kaufen", NamedTextColor.GREEN)));
            sm.put(i, l.getId());
        }

        setNav(inv, page, total, true);
        inv.setItem(49, ctrl(Material.CHEST, Component.text("Auktionshaus", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Seite " + (page + 1) + "/" + total, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text(all.size() + " Angebote", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))));
        inv.setItem(50, ctrl(Material.BOOK, Component.text("Meine Angebote", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false), List.of()));
        fillGlass(inv, 45, 53, new int[]{45, 49, 50, 53});

        browsePage.put(player.getUniqueId(), page);
        slotMap.put(player.getUniqueId(), sm);
        viewState.put(player.getUniqueId(), "browse");
        player.openInventory(inv);
    }

    public void openMine(Player player, int page) {
        List<AuctionListing> mine = auction.getListingsByPlayer(player.getUniqueId());
        int total = Math.max(1, (int) Math.ceil(mine.size() / (double) PAGE_SIZE));
        page = Math.clamp(page, 0, total - 1);

        Inventory inv = Bukkit.createInventory(null, 54, T_MINE);
        Map<Integer, UUID> sm = new HashMap<>();

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < mine.size(); i++) {
            AuctionListing l = mine.get(start + i);
            inv.setItem(i, buildListingItem(l,
                    Component.text("Linksklick zum Stornieren", NamedTextColor.RED)));
            sm.put(i, l.getId());
        }

        setNav(inv, page, total, false);
        inv.setItem(48, ctrl(Material.BARRIER, Component.text("Zurück zum Markt", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false), List.of()));
        inv.setItem(49, ctrl(Material.BOOK, Component.text("Meine Angebote", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false),
                List.of(Component.text(mine.size() + "/" + AuctionManager.MAX_PER_PLAYER + " Angebote", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))));
        fillGlass(inv, 45, 53, new int[]{45, 48, 49, 53});

        minePage.put(player.getUniqueId(), page);
        slotMap.put(player.getUniqueId(), sm);
        viewState.put(player.getUniqueId(), "mine");
        player.openInventory(inv);
    }

    private void openConfirm(Player player, AuctionListing listing) {
        Inventory inv = Bukkit.createInventory(null, 27, T_CONFIRM);

        inv.setItem(11, ctrl(Material.LIME_WOOL,
                Component.text("Kaufen", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Preis: " + listing.getPrice() + " Coins", NamedTextColor.GOLD)
                                .decoration(TextDecoration.ITALIC, false))));
        inv.setItem(13, buildListingItem(listing, null));
        inv.setItem(15, ctrl(Material.RED_WOOL,
                Component.text("Abbrechen", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of()));

        for (int s = 0; s < 27; s++) {
            if (inv.getItem(s) == null)
                inv.setItem(s, ctrl(Material.GRAY_STAINED_GLASS_PANE,
                        Component.text(" ").decoration(TextDecoration.ITALIC, false), List.of()));
        }

        confirmPending.put(player.getUniqueId(), listing.getId());
        viewState.put(player.getUniqueId(), "confirm");
        player.openInventory(inv);
    }

    // ── Event-Handler ─────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(T_BROWSE) && !title.equals(T_MINE) && !title.equals(T_CONFIRM)) return;

        event.setCancelled(true);
        // Nur Klicks im oberen Inventory
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();
        String view = viewState.getOrDefault(player.getUniqueId(), "");

        switch (view) {
            case "browse"  -> handleBrowse(player, slot);
            case "mine"    -> handleMine(player, slot);
            case "confirm" -> handleConfirm(player, slot);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        browsePage.remove(uuid);
        minePage.remove(uuid);
        slotMap.remove(uuid);
        confirmPending.remove(uuid);
        viewState.remove(uuid);
    }

    // ── Click-Handler ─────────────────────────────────────────────────────

    private void handleBrowse(Player player, int slot) {
        int page  = browsePage.getOrDefault(player.getUniqueId(), 0);
        int total = Math.max(1, (int) Math.ceil(auction.getListings().size() / (double) PAGE_SIZE));

        if (slot == 45 && page > 0)           { openBrowse(player, page - 1); return; }
        if (slot == 53 && page < total - 1)   { openBrowse(player, page + 1); return; }
        if (slot == 50)                        { openMine(player, 0); return; }
        if (slot >= PAGE_SIZE) return;

        UUID listingId = slotMap.getOrDefault(player.getUniqueId(), Map.of()).get(slot);
        if (listingId == null) return;
        AuctionListing listing = auction.getListing(listingId);
        if (listing == null) { player.sendMessage("§cAngebot nicht mehr verfügbar."); openBrowse(player, page); return; }
        if (listing.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§cDu kannst dein eigenes Angebot nicht kaufen.");
            return;
        }
        openConfirm(player, listing);
    }

    private void handleMine(Player player, int slot) {
        int page  = minePage.getOrDefault(player.getUniqueId(), 0);
        int total = Math.max(1, (int) Math.ceil(
                auction.getListingsByPlayer(player.getUniqueId()).size() / (double) PAGE_SIZE));

        if (slot == 45 && page > 0)          { openMine(player, page - 1); return; }
        if (slot == 53 && page < total - 1)  { openMine(player, page + 1); return; }
        if (slot == 48)                       { openBrowse(player, 0); return; }
        if (slot >= PAGE_SIZE) return;

        UUID listingId = slotMap.getOrDefault(player.getUniqueId(), Map.of()).get(slot);
        if (listingId == null) return;
        AuctionListing listing = auction.removeListing(listingId, player.getUniqueId(), false);
        if (listing == null) { player.sendMessage("§cAngebot nicht gefunden."); openMine(player, 0); return; }

        giveOrDrop(player, listing.getItem());
        player.sendMessage("§aAngebot storniert – Item zurückgegeben.");
        openMine(player, 0);
    }

    private void handleConfirm(Player player, int slot) {
        UUID listingId = confirmPending.remove(player.getUniqueId());
        if (listingId == null) { player.closeInventory(); return; }

        if (slot == 15) { // Abbrechen
            openBrowse(player, browsePage.getOrDefault(player.getUniqueId(), 0));
            return;
        }
        if (slot != 11) return; // Kauf-Button

        AuctionListing listing = auction.getListing(listingId);
        if (listing == null) {
            player.sendMessage("§cAngebot nicht mehr verfügbar.");
            openBrowse(player, 0);
            return;
        }
        if (!plugin.getEconomyManager().has(player.getUniqueId(), listing.getPrice())) {
            player.sendMessage("§cNicht genug Coins! Benötigt: §6" + listing.getPrice());
            openBrowse(player, browsePage.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        // Transaktion
        auction.removeListing(listingId, listing.getSellerUuid(), true);
        plugin.getEconomyManager().withdraw(player.getUniqueId(), listing.getPrice());
        plugin.getEconomyManager().deposit(listing.getSellerUuid(), listing.getPrice());
        giveOrDrop(player, listing.getItem());

        player.sendMessage("§aGekauft für §6" + listing.getPrice() + " Coins§a!");

        Player seller = Bukkit.getPlayer(listing.getSellerUuid());
        if (seller != null) {
            seller.sendMessage("§6" + player.getName() + " §ahat dein Angebot für §6"
                    + listing.getPrice() + " Coins §agekauft!");
        }
        openBrowse(player, 0);
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private ItemStack buildListingItem(AuctionListing listing, Component hint) {
        ItemStack display = listing.getItem().clone();
        ItemMeta  meta    = display.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Verkäufer: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(listing.getSellerName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("Preis: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(listing.getPrice() + " Coins", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        if (hint != null) {
            lore.add(Component.empty());
            lore.add(hint.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack ctrl(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void setNav(Inventory inv, int page, int total, boolean hasMyListings) {
        if (page > 0)
            inv.setItem(45, ctrl(Material.ARROW,
                    Component.text("Vorherige Seite", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false), List.of()));
        if (page < total - 1)
            inv.setItem(53, ctrl(Material.ARROW,
                    Component.text("Nächste Seite", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false), List.of()));
    }

    private void fillGlass(Inventory inv, int from, int to, int[] skip) {
        Set<Integer> skipSet = new HashSet<>();
        for (int s : skip) skipSet.add(s);
        ItemStack glass = ctrl(Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" ").decoration(TextDecoration.ITALIC, false), List.of());
        for (int s = from; s <= to; s++) {
            if (!skipSet.contains(s) && inv.getItem(s) == null) inv.setItem(s, glass);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }
}
