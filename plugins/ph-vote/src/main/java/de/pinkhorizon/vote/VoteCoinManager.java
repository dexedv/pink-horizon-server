package de.pinkhorizon.vote;

import de.pinkhorizon.core.PHCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class VoteCoinManager {

    private final PHVote plugin;

    public VoteCoinManager(PHVote plugin) {
        this.plugin = plugin;
        createTable();
    }

    private void createTable() {
        try (Connection c = conn();
             PreparedStatement st = c.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS vote_coins (
                     uuid        CHAR(36)    NOT NULL PRIMARY KEY,
                     name        VARCHAR(16) NOT NULL,
                     coins       INT         NOT NULL DEFAULT 0,
                     total_votes INT         NOT NULL DEFAULT 0,
                     last_vote   BIGINT      DEFAULT NULL
                 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                 """)) {
            st.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("VoteCoinManager.createTable: " + e.getMessage());
        }
    }

    /** Aktuellen Kontostand abrufen. */
    public int getCoins(UUID uuid) {
        try (Connection c = conn();
             PreparedStatement st = c.prepareStatement(
                 "SELECT coins FROM vote_coins WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("coins");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("VoteCoinManager.getCoins: " + e.getMessage());
        }
        return 0;
    }

    /** Gesamtzahl aller Votes. */
    public int getTotalVotes(UUID uuid) {
        try (Connection c = conn();
             PreparedStatement st = c.prepareStatement(
                 "SELECT total_votes FROM vote_coins WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("total_votes");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("VoteCoinManager.getTotalVotes: " + e.getMessage());
        }
        return 0;
    }

    /** Coins hinzufügen (und total_votes erhöhen falls fromVote=true). */
    public void addCoins(UUID uuid, String name, int amount, boolean fromVote) {
        try (Connection c = conn();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO vote_coins (uuid, name, coins, total_votes, last_vote)
                 VALUES (?, ?, ?, ?, ?)
                 ON DUPLICATE KEY UPDATE
                     name        = VALUES(name),
                     coins       = coins + VALUES(coins),
                     total_votes = total_votes + ?,
                     last_vote   = IF(? > 0, VALUES(last_vote), last_vote)
                 """)) {
            long now = System.currentTimeMillis();
            st.setString(1, uuid.toString());
            st.setString(2, name);
            st.setInt(3, amount);
            st.setInt(4, fromVote ? 1 : 0);
            st.setLong(5, fromVote ? now : 0);
            st.setInt(6, fromVote ? 1 : 0);
            st.setInt(7, fromVote ? 1 : 0);
            st.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("VoteCoinManager.addCoins: " + e.getMessage());
        }
    }

    /** Coins abziehen. Gibt false zurück wenn nicht genug vorhanden. */
    public boolean removeCoins(UUID uuid, int amount) {
        try (Connection c = conn();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE vote_coins SET coins = coins - ? WHERE uuid = ? AND coins >= ?")) {
            st.setInt(1, amount);
            st.setString(2, uuid.toString());
            st.setInt(3, amount);
            return st.executeUpdate() > 0;
        } catch (Exception e) {
            plugin.getLogger().warning("VoteCoinManager.removeCoins: " + e.getMessage());
            return false;
        }
    }

    /** Coins direkt setzen. */
    public void setCoins(UUID uuid, String name, int amount) {
        try (Connection c = conn();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO vote_coins (uuid, name, coins) VALUES (?, ?, ?)
                 ON DUPLICATE KEY UPDATE name = VALUES(name), coins = VALUES(coins)
                 """)) {
            st.setString(1, uuid.toString());
            st.setString(2, name);
            st.setInt(3, amount);
            st.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("VoteCoinManager.setCoins: " + e.getMessage());
        }
    }

    /** Prüft ob ein Spieler heute bereits gevotet hat (Cooldown 22h). */
    public boolean hasVotedRecently(UUID uuid) {
        try (Connection c = conn();
             PreparedStatement st = c.prepareStatement(
                 "SELECT last_vote FROM vote_coins WHERE uuid = ? AND last_vote > ?")) {
            st.setString(1, uuid.toString());
            st.setLong(2, System.currentTimeMillis() - 22 * 60 * 60 * 1000L);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private Connection conn() throws Exception {
        return PHCore.getInstance().getDatabaseManager().getConnection();
    }
}
