package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UpgradeManager {

    private final PHSurvival plugin;

    // In-memory cache — frequently checked (death, movement, login)
    private final Map<UUID, Boolean> keepInventoryPerm = new HashMap<>();
    private final Map<UUID, Boolean> flyPerm           = new HashMap<>();
    private final Map<UUID, Long>    kiExpiry          = new HashMap<>();
    private final Map<UUID, Long>    flyExpiry         = new HashMap<>();
    private final Map<UUID, Integer> extraClaims       = new HashMap<>();
    private final Map<UUID, Integer> claimPurchases    = new HashMap<>();
    private final Map<UUID, Integer> extraHomes        = new HashMap<>();

    public UpgradeManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    // ── KeepInventory ────────────────────────────────────────────────────

    public boolean hasActiveKI(UUID uuid) {
        ensureLoaded(uuid);
        if (keepInventoryPerm.getOrDefault(uuid, false)) return true;
        return isActive(kiExpiry, uuid);
    }

    public boolean hasPermKI(UUID uuid) {
        ensureLoaded(uuid);
        return keepInventoryPerm.getOrDefault(uuid, false);
    }

    public long getKiRemainingMs(UUID uuid) {
        ensureLoaded(uuid);
        if (keepInventoryPerm.getOrDefault(uuid, false)) return Long.MAX_VALUE;
        return remainingMs(kiExpiry, uuid);
    }

    public void givePermKI(UUID uuid) {
        keepInventoryPerm.put(uuid, true);
        persist(uuid);
    }

    public void grantTempKI(UUID uuid, long durationMs) {
        ensureLoaded(uuid);
        long current   = System.currentTimeMillis();
        long existing  = kiExpiry.getOrDefault(uuid, current);
        long newExpiry = Math.max(existing, current) + durationMs;
        kiExpiry.put(uuid, newExpiry);
        persist(uuid);
    }

    // ── Fly ──────────────────────────────────────────────────────────────

    public boolean hasPermFly(UUID uuid) {
        ensureLoaded(uuid);
        return flyPerm.getOrDefault(uuid, false);
    }

    public void givePermFly(Player player) {
        flyPerm.put(player.getUniqueId(), true);
        persist(player.getUniqueId());
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    public boolean hasActiveFly(UUID uuid) {
        ensureLoaded(uuid);
        return flyPerm.getOrDefault(uuid, false) || isActive(flyExpiry, uuid);
    }

    public long getFlyRemainingMs(UUID uuid) {
        ensureLoaded(uuid);
        return remainingMs(flyExpiry, uuid);
    }

    public void grantFly(Player player, long durationMs) {
        UUID uuid      = player.getUniqueId();
        ensureLoaded(uuid);
        long current   = System.currentTimeMillis();
        long existing  = flyExpiry.getOrDefault(uuid, current);
        long newExpiry = Math.max(existing, current) + durationMs;

        flyExpiry.put(uuid, newExpiry);
        persist(uuid);

        player.setAllowFlight(true);
        player.setFlying(true);

        long ticks = (newExpiry - System.currentTimeMillis()) / 50;
        if (!flyPerm.getOrDefault(uuid, false)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!hasActiveFly(uuid)) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.sendMessage("§cDein Flug-Booster ist abgelaufen!");
                }
            }, ticks);
        }
    }

    public void restoreFly(Player player) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(uuid);
        if (flyPerm.getOrDefault(uuid, false)) {
            player.setAllowFlight(true);
            player.sendMessage("§aFly §ldauerhaft§r§a aktiv.");
            return;
        }
        if (isActive(flyExpiry, uuid)) {
            player.setAllowFlight(true);
            long mins = getFlyRemainingMs(uuid) / 60_000;
            player.sendMessage("§aFly aktiv – noch §f" + mins + " §aMinuten.");

            long ticks = getFlyRemainingMs(uuid) / 50;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!hasActiveFly(uuid)) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.sendMessage("§cDein Flug-Booster ist abgelaufen!");
                }
            }, ticks);
        }
    }

    // ── Extra Claims ─────────────────────────────────────────────────────

    public int getExtraClaims(UUID uuid) {
        ensureLoaded(uuid);
        return extraClaims.getOrDefault(uuid, 0);
    }

    public void addExtraClaims(UUID uuid, int amount) {
        ensureLoaded(uuid);
        extraClaims.put(uuid, extraClaims.getOrDefault(uuid, 0) + amount);
        persist(uuid);
    }

    public int getClaimPurchases(UUID uuid) {
        ensureLoaded(uuid);
        return claimPurchases.getOrDefault(uuid, 0);
    }

    public void incrementClaimPurchases(UUID uuid) {
        ensureLoaded(uuid);
        claimPurchases.put(uuid, claimPurchases.getOrDefault(uuid, 0) + 1);
        persist(uuid);
    }

    /** Preis für den nächsten Claim-Slot: (bereits gekaufte + 1) × 10.000 Coins. */
    public long getNextClaimPrice(UUID uuid) {
        return (long) (getExtraClaims(uuid) + 1) * 10_000L;
    }

    /** Fügt einen einzelnen Extra-Claim-Slot hinzu. Max 50 Extras. */
    public void addOneExtraClaim(UUID uuid) {
        ensureLoaded(uuid);
        extraClaims.put(uuid, Math.min(extraClaims.getOrDefault(uuid, 0) + 1, 50));
        claimPurchases.put(uuid, claimPurchases.getOrDefault(uuid, 0) + 1);
        persist(uuid);
    }

    // ── Extra Homes ──────────────────────────────────────────────────────

    /** Bereits gekaufte Extra-Home-Slots (0–10). */
    public int getExtraHomes(UUID uuid) {
        ensureLoaded(uuid);
        return extraHomes.getOrDefault(uuid, 0);
    }

    /** Preis für den nächsten Home-Slot: (gekaufte + 1) × 100.000 Coins. */
    public long getNextHomePrice(UUID uuid) {
        return (long) (getExtraHomes(uuid) + 1) * 100_000L;
    }

    /** Fügt einen Extra-Home-Slot hinzu. Max 10 Extras. */
    public void addExtraHome(UUID uuid) {
        ensureLoaded(uuid);
        extraHomes.put(uuid, Math.min(extraHomes.getOrDefault(uuid, 0) + 1, 10));
        persist(uuid);
    }

    // ── Intern ───────────────────────────────────────────────────────────

    private boolean isActive(Map<UUID, Long> map, UUID uuid) {
        long expiry = map.getOrDefault(uuid, 0L);
        if (expiry <= 0) return false;
        if (System.currentTimeMillis() > expiry) { map.remove(uuid); return false; }
        return true;
    }

    private long remainingMs(Map<UUID, Long> map, UUID uuid) {
        return Math.max(0, map.getOrDefault(uuid, 0L) - System.currentTimeMillis());
    }

    /** Load all players at startup. */
    private void load() {
        long now = System.currentTimeMillis();
        try (Connection c = con();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT uuid, keep_inventory, fly_perm, ki_expiry, fly_expiry, extra_claims, claim_purchases, extra_homes" +
                 " FROM sv_upgrades")) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    keepInventoryPerm.put(uuid, rs.getBoolean("keep_inventory"));
                    flyPerm.put(uuid,           rs.getBoolean("fly_perm"));
                    extraClaims.put(uuid,       rs.getInt("extra_claims"));
                    claimPurchases.put(uuid,    rs.getInt("claim_purchases"));
                    extraHomes.put(uuid,        rs.getInt("extra_homes"));
                    long ki  = rs.getLong("ki_expiry");
                    long fly = rs.getLong("fly_expiry");
                    if (ki  > now) kiExpiry.put(uuid, ki);
                    if (fly > now) flyExpiry.put(uuid, fly);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("UpgradeManager.load: " + e.getMessage());
        }
    }

    /** Ensure a player's data is loaded (lazy load new players). */
    private void ensureLoaded(UUID uuid) {
        if (keepInventoryPerm.containsKey(uuid)) return;
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT keep_inventory, fly_perm, ki_expiry, fly_expiry, extra_claims, claim_purchases" +
                 " FROM sv_upgrades WHERE uuid=?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    long now = System.currentTimeMillis();
                    keepInventoryPerm.put(uuid, rs.getBoolean("keep_inventory"));
                    flyPerm.put(uuid,           rs.getBoolean("fly_perm"));
                    extraClaims.put(uuid,       rs.getInt("extra_claims"));
                    claimPurchases.put(uuid,    rs.getInt("claim_purchases"));
                    extraHomes.put(uuid,        rs.getInt("extra_homes"));
                    long ki  = rs.getLong("ki_expiry");
                    long fly = rs.getLong("fly_expiry");
                    if (ki  > now) kiExpiry.put(uuid, ki);
                    if (fly > now) flyExpiry.put(uuid, fly);
                } else {
                    // New player — initialize with defaults
                    keepInventoryPerm.put(uuid, false);
                    flyPerm.put(uuid, false);
                    extraClaims.put(uuid, 0);
                    claimPurchases.put(uuid, 0);
                    extraHomes.put(uuid, 0);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("UpgradeManager.ensureLoaded: " + e.getMessage());
        }
    }

    private void persist(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_upgrades (uuid, keep_inventory, fly_perm, ki_expiry, fly_expiry, extra_claims, claim_purchases, extra_homes)" +
                 " VALUES (?,?,?,?,?,?,?,?)" +
                 " ON DUPLICATE KEY UPDATE" +
                 " keep_inventory=VALUES(keep_inventory), fly_perm=VALUES(fly_perm)," +
                 " ki_expiry=VALUES(ki_expiry), fly_expiry=VALUES(fly_expiry)," +
                 " extra_claims=VALUES(extra_claims), claim_purchases=VALUES(claim_purchases)," +
                 " extra_homes=VALUES(extra_homes)")) {
            st.setString(1, uuid.toString());
            st.setBoolean(2, keepInventoryPerm.getOrDefault(uuid, false));
            st.setBoolean(3, flyPerm.getOrDefault(uuid, false));
            st.setLong(4, kiExpiry.getOrDefault(uuid, 0L));
            st.setLong(5, flyExpiry.getOrDefault(uuid, 0L));
            st.setInt(6, extraClaims.getOrDefault(uuid, 0));
            st.setInt(7, claimPurchases.getOrDefault(uuid, 0));
            st.setInt(8, extraHomes.getOrDefault(uuid, 0));
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("UpgradeManager.persist: " + e.getMessage());
        }
    }
}
