package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;

public class AuctionManager {

    private static final long EXPIRE_MS      = 7L * 24 * 60 * 60 * 1000; // 7 Tage
    private final PHSurvival plugin;
    // In-memory list for fast GUI access
    private final List<AuctionListing> listings = new ArrayList<>();

    public AuctionManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    // ── API ───────────────────────────────────────────────────────────────

    public static int getMaxSlots(org.bukkit.entity.Player player) {
        return player.hasPermission("survival.ah.vip") ? 10 : 5;
    }

    public String addListing(UUID sellerUuid, String sellerName, ItemStack item, long price, int maxSlots) {
        long count = listings.stream().filter(l -> l.getSellerUuid().equals(sellerUuid)).count();
        if (count >= maxSlots)
            return "Du hast bereits " + maxSlots + " aktive Angebote! (Dein Limit)";

        UUID   id       = UUID.randomUUID();
        long   listedAt = System.currentTimeMillis();
        String itemData = Base64.getEncoder().encodeToString(item.serializeAsBytes());

        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_auction (id, seller_uuid, seller_name, item_data, price, listed_at)" +
                 " VALUES (?,?,?,?,?,?)")) {
            st.setString(1, id.toString());
            st.setString(2, sellerUuid.toString());
            st.setString(3, sellerName);
            st.setString(4, itemData);
            st.setLong(5, price);
            st.setLong(6, listedAt);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("AuctionManager.addListing: " + e.getMessage());
            return "Datenbankfehler beim Einstellen.";
        }

        listings.add(new AuctionListing(id, sellerUuid, sellerName, item, price, listedAt));
        return null;
    }

    public AuctionListing removeListing(UUID listingId, UUID requesterUuid, boolean admin) {
        Iterator<AuctionListing> it = listings.iterator();
        while (it.hasNext()) {
            AuctionListing l = it.next();
            if (!l.getId().equals(listingId)) continue;
            if (!admin && !l.getSellerUuid().equals(requesterUuid)) return null;

            try (Connection c = con();
                 PreparedStatement st = c.prepareStatement(
                     "DELETE FROM sv_auction WHERE id=?")) {
                st.setString(1, listingId.toString());
                st.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("AuctionManager.removeListing: " + e.getMessage());
            }

            it.remove();
            return l;
        }
        return null;
    }

    public AuctionListing getListing(UUID listingId) {
        return listings.stream().filter(l -> l.getId().equals(listingId)).findFirst().orElse(null);
    }

    public List<AuctionListing> getListings()                    { return Collections.unmodifiableList(listings); }
    public List<AuctionListing> getListingsByPlayer(UUID uuid)   { return listings.stream().filter(l -> l.getSellerUuid().equals(uuid)).toList(); }

    /** No-op — writes go directly to DB. Kept for API compatibility. */
    public void save() {}

    // ── Persistenz ────────────────────────────────────────────────────────

    private void load() {
        long now = System.currentTimeMillis();
        try (Connection c = con();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, seller_uuid, seller_name, item_data, price, listed_at FROM sv_auction")) {
            while (rs.next()) {
                try {
                    long listedAt = rs.getLong("listed_at");
                    if (now - listedAt > EXPIRE_MS) {
                        // Expired — clean up from DB
                        try (PreparedStatement del = c.prepareStatement(
                                 "DELETE FROM sv_auction WHERE id=?")) {
                            del.setString(1, rs.getString("id"));
                            del.executeUpdate();
                        }
                        continue;
                    }
                    UUID      id       = UUID.fromString(rs.getString("id"));
                    UUID      seller   = UUID.fromString(rs.getString("seller_uuid"));
                    String    name     = rs.getString("seller_name");
                    long      price    = rs.getLong("price");
                    byte[]    bytes    = Base64.getDecoder().decode(rs.getString("item_data"));
                    ItemStack item     = ItemStack.deserializeBytes(bytes);
                    listings.add(new AuctionListing(id, seller, name, item, price, listedAt));
                } catch (Exception e) {
                    plugin.getLogger().warning("AuctionManager.load entry: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("AuctionManager.load: " + e.getMessage());
        }
        plugin.getLogger().info("Auktionshaus: " + listings.size() + " Angebote geladen.");
    }
}
