package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;

import java.sql.*;
import java.util.*;

public class FriendManager {

    private final PHSurvival plugin;

    public FriendManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    public Set<UUID> getFriends(UUID uuid) {
        Set<UUID> result = new HashSet<>();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT uuid2 FROM sv_friends WHERE uuid1=?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) result.add(UUID.fromString(rs.getString(1)));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("FriendManager.getFriends: " + e.getMessage());
        }
        return result;
    }

    public boolean areFriends(UUID a, UUID b) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT 1 FROM sv_friends WHERE uuid1=? AND uuid2=?")) {
            st.setString(1, a.toString());
            st.setString(2, b.toString());
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("FriendManager.areFriends: " + e.getMessage());
        }
        return false;
    }

    public void addFriend(UUID a, UUID b) {
        try (Connection c = con()) {
            try (PreparedStatement st = c.prepareStatement(
                     "INSERT IGNORE INTO sv_friends (uuid1, uuid2) VALUES (?,?),(?,?)")) {
                st.setString(1, a.toString()); st.setString(2, b.toString());
                st.setString(3, b.toString()); st.setString(4, a.toString());
                st.executeUpdate();
            }
            // Remove any pending requests both ways
            try (PreparedStatement st = c.prepareStatement(
                     "DELETE FROM sv_friend_requests WHERE (from_uuid=? AND to_uuid=?) OR (from_uuid=? AND to_uuid=?)")) {
                st.setString(1, a.toString()); st.setString(2, b.toString());
                st.setString(3, b.toString()); st.setString(4, a.toString());
                st.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("FriendManager.addFriend: " + e.getMessage());
        }
    }

    public void removeFriend(UUID a, UUID b) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "DELETE FROM sv_friends WHERE (uuid1=? AND uuid2=?) OR (uuid1=? AND uuid2=?)")) {
            st.setString(1, a.toString()); st.setString(2, b.toString());
            st.setString(3, b.toString()); st.setString(4, a.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("FriendManager.removeFriend: " + e.getMessage());
        }
    }

    public boolean hasPendingRequest(UUID from, UUID to) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT 1 FROM sv_friend_requests WHERE from_uuid=? AND to_uuid=?")) {
            st.setString(1, from.toString());
            st.setString(2, to.toString());
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("FriendManager.hasPendingRequest: " + e.getMessage());
        }
        return false;
    }

    public void sendRequest(UUID from, UUID to) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT IGNORE INTO sv_friend_requests (from_uuid, to_uuid) VALUES (?,?)")) {
            st.setString(1, from.toString());
            st.setString(2, to.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("FriendManager.sendRequest: " + e.getMessage());
        }
    }

    public void removeRequest(UUID from, UUID to) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "DELETE FROM sv_friend_requests WHERE from_uuid=? AND to_uuid=?")) {
            st.setString(1, from.toString());
            st.setString(2, to.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("FriendManager.removeRequest: " + e.getMessage());
        }
    }

    public List<UUID> getIncomingRequests(UUID target) {
        List<UUID> result = new ArrayList<>();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT from_uuid FROM sv_friend_requests WHERE to_uuid=?")) {
            st.setString(1, target.toString());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) result.add(UUID.fromString(rs.getString(1)));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("FriendManager.getIncomingRequests: " + e.getMessage());
        }
        return result;
    }
}
