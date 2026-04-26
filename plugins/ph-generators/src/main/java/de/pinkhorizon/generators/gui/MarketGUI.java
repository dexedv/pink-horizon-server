package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.MarketManager;
import de.pinkhorizon.generators.managers.MoneyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI für den Spieler-Marktplatz (Upgrade-Token Handel).
 */
public class MarketGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "⚖ Marktplatz";

    public MarketGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MM.deserialize("<gold>" + TITLE));
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());

        // Hintergrund
        ItemStack bg = filler(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        // Spieler-Info (obere Reihe links)
        if (data != null) {
            ItemStack info = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta im = info.getItemMeta();
            im.displayName(MM.deserialize("<white>" + player.getName()));
            im.lore(List.of(
                MM.deserialize("<gray>Guthaben: <green>$" + MoneyManager.formatMoney(data.getMoney())),
                MM.deserialize("<gray>Upgrade-Tokens: <aqua>" + data.getUpgradeTokens())
            ));
            info.setItemMeta(im);
            inv.setItem(0, info);
        }

        // Einstellen-Button
        if (data != null && data.getUpgradeTokens() > 0) {
            inv.setItem(2, buildListButton(data));
        }

        // Angebote laden (Slots 9–44)
        List<Object[]> listings = plugin.getMarketManager().getCachedListings();
        int slot = 9;
        for (Object[] listing : listings) {
            if (slot > 44) break;
            int id = (int) listing[0];
            String sellerUuid = (String) listing[1];
            String itemType = (String) listing[2];
            long price = (Long) listing[3];

            boolean ownListing = sellerUuid.equals(player.getUniqueId().toString());
            inv.setItem(slot, buildListingItem(id, itemType, price, ownListing, data));
            slot++;
        }

        if (listings.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta em = empty.getItemMeta();
            em.displayName(MM.deserialize("<gray>Keine Angebote verfügbar"));
            em.lore(List.of(MM.deserialize("<dark_gray>Sei der Erste!"),
                            MM.deserialize("<dark_gray>Klicke 'Einzel Token einstellen'"),
                            MM.deserialize("<dark_gray>oben links.")));
            empty.setItemMeta(em);
            inv.setItem(22, empty);
        }

        // Schließen
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(MM.deserialize("<red>Schließen"));
        close.setItemMeta(cm);
        inv.setItem(49, close);

        // Aktualisieren
        ItemStack refresh = new ItemStack(Material.CLOCK);
        ItemMeta rm = refresh.getItemMeta();
        rm.displayName(MM.deserialize("<yellow>Aktualisieren"));
        refresh.setItemMeta(rm);
        inv.setItem(51, refresh);

        player.openInventory(inv);
    }

    private ItemStack buildListButton(PlayerData data) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<aqua>✦ Token einstellen"));
        meta.lore(List.of(
            MM.deserialize("<gray>Token: <aqua>" + data.getUpgradeTokens()),
            MM.deserialize(""),
            MM.deserialize("<dark_gray>Preis-Stufen:"),
            MM.deserialize("<dark_gray>• 1 = 50.000$"),
            MM.deserialize("<dark_gray>• 2 = 100.000$"),
            MM.deserialize("<dark_gray>• 3 = 250.000$"),
            MM.deserialize("<dark_gray>• 4 = 500.000$"),
            MM.deserialize("<dark_gray>• 5 = 1.000.000$"),
            MM.deserialize(""),
            MM.deserialize("<yellow>▶ Klicken: 50k | Shift: 250k | Rechts: 1M")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildListingItem(int id, String itemType, long price, boolean ownListing, PlayerData data) {
        Material mat = "UPGRADE_TOKEN".equals(itemType) ? Material.EMERALD : Material.PAPER;
        String label = "UPGRADE_TOKEN".equals(itemType) ? "<aqua>Upgrade-Token" : itemType;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(label));

        boolean canAfford = data != null && data.getMoney() >= price;
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Preis: <green>$" + MoneyManager.formatMoney(price)));
        lore.add(MM.deserialize(""));
        if (ownListing) {
            lore.add(MM.deserialize("<yellow>▶ Klicken: Angebot zurückziehen"));
        } else if (canAfford) {
            lore.add(MM.deserialize("<green>▶ Klicken: Kaufen"));
        } else {
            lore.add(MM.deserialize("<red>Nicht genug Geld"));
        }
        meta.lore(lore);

        // Store listing ID in item name for identification
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey("ph-generators", "market_listing_id"),
            org.bukkit.persistence.PersistentDataType.INTEGER, id
        );

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!MM.serialize(e.getView().title()).contains(TITLE)) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        if (slot == 49) { player.closeInventory(); return; }

        if (slot == 51) {
            // Refresh
            plugin.getMarketManager().refreshCache();
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 20L);
            player.sendMessage(MM.deserialize("<gray>Marktplatz wird aktualisiert..."));
            return;
        }

        // List button
        if (slot == 2 && data.getUpgradeTokens() > 0) {
            boolean shift = e.isShiftClick();
            boolean right = e.isRightClick();
            long price;
            if (right) price = 1_000_000L;
            else if (shift) price = 250_000L;
            else price = 50_000L;

            MarketManager.ListResult result = plugin.getMarketManager().listToken(player, data, price);
            if (result == MarketManager.ListResult.SUCCESS) {
                open(player);
            }
            return;
        }

        // Listing slots (9-44)
        if (slot >= 9 && slot <= 44) {
            ItemStack item = e.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;
            Integer listingId = item.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("ph-generators", "market_listing_id"),
                org.bukkit.persistence.PersistentDataType.INTEGER
            );
            if (listingId == null) return;

            boolean ownListing = false;
            for (Object[] l : plugin.getMarketManager().getCachedListings()) {
                if ((int) l[0] == listingId && ((String) l[1]).equals(player.getUniqueId().toString())) {
                    ownListing = true;
                    break;
                }
            }

            if (ownListing) {
                plugin.getMarketManager().cancelListing(player, data, listingId);
                open(player);
            } else {
                MarketManager.BuyResult result = plugin.getMarketManager().buy(player, data, listingId);
                switch (result) {
                    case NO_MONEY -> player.sendMessage(MM.deserialize("<red>Nicht genug Geld!"));
                    case NOT_FOUND -> player.sendMessage(MM.deserialize("<red>Angebot nicht mehr verfügbar!"));
                    case OWN_LISTING -> player.sendMessage(MM.deserialize("<red>Du kannst dein eigenes Angebot nicht kaufen!"));
                    case SUCCESS -> {}
                }
                open(player);
            }
        }
    }

    private ItemStack filler(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        item.setItemMeta(meta);
        return item;
    }
}
