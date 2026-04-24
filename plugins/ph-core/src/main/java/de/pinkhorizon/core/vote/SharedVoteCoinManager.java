package de.pinkhorizon.core.vote;

import de.pinkhorizon.core.PHCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Zugriff auf die vote_coins-Tabelle (pinkhorizon DB).
 * Kann von jedem Plugin genutzt werden, das ph-core als Dependency hat.
 * Die Tabelle wird von ph-vote auf dem Lobby-Server erstellt.
 */
public class SharedVoteCoinManager {

    private static final SharedVoteCoinManager INSTANCE = new SharedVoteCoinManager();
    public  static SharedVoteCoinManager getInstance() { return INSTANCE; }
    private SharedVoteCoinManager() {}

    public int getCoins(UUID uuid) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT coins FROM vote_coins WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("coins");
            }
        } catch (Exception e) {
            PHCore.getInstance().getLogger().warning("[VoteCoins] getCoins: " + e.getMessage());
        }
        return 0;
    }

    public boolean removeCoins(UUID uuid, int amount) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE vote_coins SET coins = coins - ? WHERE uuid = ? AND coins >= ?")) {
            ps.setInt(1, amount);
            ps.setString(2, uuid.toString());
            ps.setInt(3, amount);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            PHCore.getInstance().getLogger().warning("[VoteCoins] removeCoins: " + e.getMessage());
            return false;
        }
    }

    private Connection conn() throws Exception {
        return PHCore.getInstance().getDatabaseManager().getConnection();
    }
}
