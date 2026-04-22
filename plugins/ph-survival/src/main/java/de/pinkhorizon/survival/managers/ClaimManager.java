package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Chunk;

import java.sql.*;
import java.util.*;

public class ClaimManager {

    private final PHSurvival plugin;
    // In-memory cache — claims are checked on every player action
    private final Map<String, UUID>        claims       = new HashMap<>();
    private final Map<UUID, Set<String>>   playerClaims = new HashMap<>();
    private final Map<String, Set<UUID>>   trust        = new HashMap<>();

    public ClaimManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    public String getKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    /** Splits "world:cx:cz" — handles world names that contain colons */
    private String[] splitKey(String key) {
        int last = key.lastIndexOf(':');
        int mid  = key.lastIndexOf(':', last - 1);
        return new String[]{ key.substring(0, mid), key.substring(mid + 1, last), key.substring(last + 1) };
    }

    public boolean isClaimed(Chunk chunk)                  { return claims.containsKey(getKey(chunk)); }
    public UUID    getOwner(Chunk chunk)                   { return claims.get(getKey(chunk)); }
    public boolean isOwner(Chunk chunk, UUID uuid)         { return uuid.equals(getOwner(chunk)); }
    public int     getClaimCount(UUID uuid)                { return playerClaims.getOrDefault(uuid, Set.of()).size(); }
    public Set<String> getPlayerClaims(UUID uuid)          { return playerClaims.getOrDefault(uuid, new HashSet<>()); }

    public boolean claim(Chunk chunk, UUID owner, int maxClaims) {
        if (getClaimCount(owner) >= maxClaims || isClaimed(chunk)) return false;
        String   key   = getKey(chunk);
        String[] parts = splitKey(key);
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT IGNORE INTO sv_claims (world, chunk_x, chunk_z, owner_uuid) VALUES (?,?,?,?)")) {
            st.setString(1, parts[0]);
            st.setInt(2, Integer.parseInt(parts[1]));
            st.setInt(3, Integer.parseInt(parts[2]));
            st.setString(4, owner.toString());
            if (st.executeUpdate() == 0) return false;
        } catch (SQLException e) {
            plugin.getLogger().warning("ClaimManager.claim: " + e.getMessage());
            return false;
        }
        claims.put(key, owner);
        playerClaims.computeIfAbsent(owner, k -> new HashSet<>()).add(key);
        return true;
    }

    public boolean claim(Chunk chunk, UUID owner) {
        return claim(chunk, owner, plugin.getConfig().getInt("claims.max-claims-per-player", 10));
    }

    public boolean unclaim(Chunk chunk, UUID requester) {
        if (!isOwner(chunk, requester)) return false;
        String   key   = getKey(chunk);
        String[] parts = splitKey(key);
        try (Connection c = con()) {
            try (PreparedStatement st = c.prepareStatement(
                     "DELETE FROM sv_claim_trusts WHERE world=? AND chunk_x=? AND chunk_z=?")) {
                st.setString(1, parts[0]); st.setInt(2, Integer.parseInt(parts[1])); st.setInt(3, Integer.parseInt(parts[2]));
                st.executeUpdate();
            }
            try (PreparedStatement st = c.prepareStatement(
                     "DELETE FROM sv_claims WHERE world=? AND chunk_x=? AND chunk_z=?")) {
                st.setString(1, parts[0]); st.setInt(2, Integer.parseInt(parts[1])); st.setInt(3, Integer.parseInt(parts[2]));
                st.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("ClaimManager.unclaim: " + e.getMessage());
            return false;
        }
        claims.remove(key);
        Set<String> ps = playerClaims.get(requester);
        if (ps != null) ps.remove(key);
        trust.remove(key);
        return true;
    }

    // ---- Key-based helpers (no chunk loading needed) ----

    public boolean isClaimedAt(String world, int cx, int cz) {
        return claims.containsKey(world + ":" + cx + ":" + cz);
    }

    public UUID getOwnerAt(String world, int cx, int cz) {
        return claims.get(world + ":" + cx + ":" + cz);
    }

    public boolean isTrustedAt(String world, int cx, int cz, UUID uuid) {
        String key = world + ":" + cx + ":" + cz;
        Set<UUID> ts = trust.get(key);
        return ts != null && ts.contains(uuid);
    }

    // ---- Trust ----

    public void trustPlayer(Chunk chunk, UUID trusted) {
        String   key   = getKey(chunk);
        String[] parts = splitKey(key);
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT IGNORE INTO sv_claim_trusts (world, chunk_x, chunk_z, trusted_uuid) VALUES (?,?,?,?)")) {
            st.setString(1, parts[0]); st.setInt(2, Integer.parseInt(parts[1])); st.setInt(3, Integer.parseInt(parts[2]));
            st.setString(4, trusted.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("ClaimManager.trustPlayer: " + e.getMessage());
        }
        trust.computeIfAbsent(key, k -> new HashSet<>()).add(trusted);
    }

    public void untrustPlayer(Chunk chunk, UUID trusted) {
        String   key   = getKey(chunk);
        String[] parts = splitKey(key);
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "DELETE FROM sv_claim_trusts WHERE world=? AND chunk_x=? AND chunk_z=? AND trusted_uuid=?")) {
            st.setString(1, parts[0]); st.setInt(2, Integer.parseInt(parts[1])); st.setInt(3, Integer.parseInt(parts[2]));
            st.setString(4, trusted.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("ClaimManager.untrustPlayer: " + e.getMessage());
        }
        Set<UUID> set = trust.get(key);
        if (set != null) { set.remove(trusted); if (set.isEmpty()) trust.remove(key); }
    }

    public boolean isTrusted(Chunk chunk, UUID player) {
        if (isOwner(chunk, player)) return true;
        Set<UUID> set = trust.get(getKey(chunk));
        return set != null && set.contains(player);
    }

    public Set<UUID> getTrusted(Chunk chunk) {
        return trust.getOrDefault(getKey(chunk), new HashSet<>());
    }

    private void load() {
        try (Connection c = con()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT world, chunk_x, chunk_z, owner_uuid FROM sv_claims")) {
                while (rs.next()) {
                    UUID   owner = UUID.fromString(rs.getString("owner_uuid"));
                    String key   = rs.getString("world") + ":" + rs.getInt("chunk_x") + ":" + rs.getInt("chunk_z");
                    claims.put(key, owner);
                    playerClaims.computeIfAbsent(owner, k -> new HashSet<>()).add(key);
                }
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT world, chunk_x, chunk_z, trusted_uuid FROM sv_claim_trusts")) {
                while (rs.next()) {
                    String key     = rs.getString("world") + ":" + rs.getInt("chunk_x") + ":" + rs.getInt("chunk_z");
                    UUID   trusted = UUID.fromString(rs.getString("trusted_uuid"));
                    trust.computeIfAbsent(key, k -> new HashSet<>()).add(trusted);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("ClaimManager.load: " + e.getMessage());
        }
    }

    /** Trusted einen Spieler auf allen Claims des Besitzers. */
    public int trustAll(UUID owner, UUID trusted) {
        Set<String> keys = playerClaims.getOrDefault(owner, new HashSet<>());
        if (keys.isEmpty()) return 0;
        int count = 0;
        for (String key : keys) {
            if (trust.getOrDefault(key, Set.of()).contains(trusted)) continue;
            String[] parts = splitKey(key);
            try (Connection c = con();
                 PreparedStatement st = c.prepareStatement(
                     "INSERT IGNORE INTO sv_claim_trusts (world, chunk_x, chunk_z, trusted_uuid) VALUES (?,?,?,?)")) {
                st.setString(1, parts[0]);
                st.setInt(2, Integer.parseInt(parts[1]));
                st.setInt(3, Integer.parseInt(parts[2]));
                st.setString(4, trusted.toString());
                st.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("ClaimManager.trustAll: " + e.getMessage());
                continue;
            }
            trust.computeIfAbsent(key, k -> new HashSet<>()).add(trusted);
            count++;
        }
        return count;
    }

    /** No-op — writes go directly to DB. Kept for API compatibility. */
    public void save() {}
}
