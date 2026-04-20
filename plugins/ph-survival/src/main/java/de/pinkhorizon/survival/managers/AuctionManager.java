package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AuctionManager {

    private static final long EXPIRE_MS          = 7L * 24 * 60 * 60 * 1000; // 7 Tage
    public  static final int  MAX_PER_PLAYER     = 10;

    private final PHSurvival plugin;
    private final File        file;
    private final List<AuctionListing> listings = new ArrayList<>();

    public AuctionManager(PHSurvival plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "auction.yml");
        load();
    }

    // ── Persistenz ────────────────────────────────────────────────────────

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.isConfigurationSection("listings")) return;

        long now = System.currentTimeMillis();
        for (String key : cfg.getConfigurationSection("listings").getKeys(false)) {
            try {
                UUID id         = UUID.fromString(key);
                UUID seller     = UUID.fromString(cfg.getString("listings." + key + ".seller"));
                String sellName = cfg.getString("listings." + key + ".sellerName", "Unbekannt");
                long  price     = cfg.getLong  ("listings." + key + ".price");
                long  listedAt  = cfg.getLong  ("listings." + key + ".listedAt");
                if (now - listedAt > EXPIRE_MS) continue;           // abgelaufen überspringen

                byte[]    bytes = Base64.getDecoder().decode(cfg.getString("listings." + key + ".item"));
                ItemStack item  = ItemStack.deserializeBytes(bytes);
                listings.add(new AuctionListing(id, seller, sellName, item, price, listedAt));
            } catch (Exception e) {
                plugin.getLogger().warning("Fehler beim Laden von Auktions-Listing " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Auktionshaus: " + listings.size() + " Angebote geladen.");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (AuctionListing l : listings) {
            String base = "listings." + l.getId() + ".";
            cfg.set(base + "seller",     l.getSellerUuid().toString());
            cfg.set(base + "sellerName", l.getSellerName());
            cfg.set(base + "price",      l.getPrice());
            cfg.set(base + "listedAt",   l.getListedAt());
            cfg.set(base + "item",       Base64.getEncoder().encodeToString(l.getItem().serializeAsBytes()));
        }
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Fehler beim Speichern der Auktionen: " + e.getMessage()); }
    }

    // ── API ───────────────────────────────────────────────────────────────

    /**
     * Listing einstellen.
     * @return Fehlermeldung oder null bei Erfolg.
     */
    public String addListing(UUID sellerUuid, String sellerName, ItemStack item, long price) {
        long count = listings.stream().filter(l -> l.getSellerUuid().equals(sellerUuid)).count();
        if (count >= MAX_PER_PLAYER)
            return "Du hast bereits " + MAX_PER_PLAYER + " aktive Angebote!";

        listings.add(new AuctionListing(
                UUID.randomUUID(), sellerUuid, sellerName, item, price, System.currentTimeMillis()));
        save();
        return null;
    }

    /**
     * Listing entfernen.
     * @param admin Wenn true, darf jeder entfernen.
     * @return Das entfernte Listing oder null wenn nicht gefunden/keine Berechtigung.
     */
    public AuctionListing removeListing(UUID listingId, UUID requesterUuid, boolean admin) {
        Iterator<AuctionListing> it = listings.iterator();
        while (it.hasNext()) {
            AuctionListing l = it.next();
            if (!l.getId().equals(listingId)) continue;
            if (!admin && !l.getSellerUuid().equals(requesterUuid)) return null;
            it.remove();
            save();
            return l;
        }
        return null;
    }

    public AuctionListing getListing(UUID listingId) {
        return listings.stream().filter(l -> l.getId().equals(listingId)).findFirst().orElse(null);
    }

    public List<AuctionListing> getListings() {
        return Collections.unmodifiableList(listings);
    }

    public List<AuctionListing> getListingsByPlayer(UUID uuid) {
        return listings.stream().filter(l -> l.getSellerUuid().equals(uuid)).toList();
    }
}
