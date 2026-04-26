package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Verwaltet den Spieler-Marktplatz (Upgrade-Tokens handeln).
 */
public class MarketManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Gecachte Listings: id → [sellerUUID, itemType, price, listedAt] */
    private final List<Object[]> cachedListings = new ArrayList<>();
    private long lastRefresh = 0;

    public MarketManager(PHGenerators plugin) {
        this.plugin = plugin;
        refreshCache();
    }

    public void refreshCache() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Object[]> listings = plugin.getRepository().getMarketListings();
            synchronized (cachedListings) {
                cachedListings.clear();
                cachedListings.addAll(listings);
            }
            lastRefresh = System.currentTimeMillis();
        });
    }

    public enum ListResult { SUCCESS, NOT_ENOUGH_ITEMS, ALREADY_LISTED, INVALID_PRICE }
    public enum BuyResult  { SUCCESS, NOT_FOUND, NO_MONEY, OWN_LISTING }

    /** Spieler listet Upgrade-Tokens zum Verkauf */
    public ListResult listToken(Player seller, PlayerData data, long price) {
        if (price <= 0) return ListResult.INVALID_PRICE;
        if (price > 10_000_000_000L) return ListResult.INVALID_PRICE;
        if (data.getUpgradeTokens() < 1) return ListResult.NOT_ENOUGH_ITEMS;

        data.setUpgradeTokens(data.getUpgradeTokens() - 1);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int id = plugin.getRepository().insertMarketListing(seller.getUniqueId(), "UPGRADE_TOKEN", price);
            refreshCache();
            Bukkit.getScheduler().runTask(plugin, () -> {
                seller.sendMessage(MM.deserialize(
                        "<green>✔ Upgrade-Token für <yellow>$" + MoneyManager.formatMoney(price)
                        + "<green> eingestellt! (ID: " + id + ")"));
            });
        });
        plugin.getRepository().savePlayer(data);
        return ListResult.SUCCESS;
    }

    /** Spieler kauft ein Listing */
    public BuyResult buy(Player buyer, PlayerData buyerData, int listingId) {
        Object[] listing = findListing(listingId);
        if (listing == null) return BuyResult.NOT_FOUND;

        String sellerUuidStr = (String) listing[1];
        long price = (Long) listing[3];

        if (sellerUuidStr.equals(buyer.getUniqueId().toString())) return BuyResult.OWN_LISTING;
        if (buyerData.getMoney() < price) return BuyResult.NO_MONEY;

        buyerData.takeMoney(price);
        buyerData.addUpgradeTokens(1);

        // Geld an Verkäufer
        UUID sellerUuid = UUID.fromString(sellerUuidStr);
        PlayerData sellerData = plugin.getPlayerDataMap().get(sellerUuid);
        if (sellerData != null) {
            sellerData.addMoney(price);
            Player sellerPlayer = Bukkit.getPlayer(sellerUuid);
            if (sellerPlayer != null) {
                sellerPlayer.sendMessage(MM.deserialize(
                        "<green>✔ Dein Token wurde von <white>" + buyer.getName()
                        + "<green> für <yellow>$" + MoneyManager.formatMoney(price) + "<green> gekauft!"));
            }
            plugin.getRepository().savePlayer(sellerData);
        } else {
            // Offline-Zahlung: direkt in DB updaten
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getRepository().addIncomeLog(sellerUuid, System.currentTimeMillis() / 3_600_000L, price));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getRepository().deleteMarketListing(listingId);
            plugin.getRepository().savePlayer(buyerData);
            refreshCache();
        });

        buyer.sendMessage(MM.deserialize(
                "<green>✔ Upgrade-Token gekauft für <yellow>$" + MoneyManager.formatMoney(price)
                + "<green>! Token: <white>" + buyerData.getUpgradeTokens()));
        return BuyResult.SUCCESS;
    }

    /** Spieler entfernt sein Listing */
    public boolean cancelListing(Player seller, PlayerData data, int listingId) {
        Object[] listing = findListing(listingId);
        if (listing == null) return false;
        if (!listing[1].equals(seller.getUniqueId().toString())) return false;

        data.addUpgradeTokens(1); // Token zurückgeben
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getRepository().deleteMarketListing(listingId);
            plugin.getRepository().savePlayer(data);
            refreshCache();
        });

        seller.sendMessage(MM.deserialize("<yellow>Listing entfernt. Token zurückgegeben."));
        return true;
    }

    public List<Object[]> getCachedListings() {
        synchronized (cachedListings) {
            return new ArrayList<>(cachedListings);
        }
    }

    private Object[] findListing(int id) {
        synchronized (cachedListings) {
            for (Object[] l : cachedListings) {
                if ((int) l[0] == id) return l;
            }
        }
        return null;
    }

    public String getListingsText() {
        List<Object[]> listings = getCachedListings();
        if (listings.isEmpty()) return "<gray>Keine Angebote im Marktplatz.";

        StringBuilder sb = new StringBuilder("<gold>━━ Marktplatz ━━\n");
        for (Object[] l : listings) {
            int id = (int) l[0];
            String type = (String) l[2];
            long price = (Long) l[3];
            String label = "UPGRADE_TOKEN".equals(type) ? "<aqua>Upgrade-Token" : type;
            sb.append("<gray>[").append(id).append("] ").append(label)
              .append(" <dark_gray>– <green>$").append(MoneyManager.formatMoney(price))
              .append(" <dark_gray>/gen market buy ").append(id).append("\n");
        }
        return sb.toString().trim();
    }
}
