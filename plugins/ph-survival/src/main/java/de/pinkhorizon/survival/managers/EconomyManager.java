package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EconomyManager {

    private final PHSurvival plugin;

    public EconomyManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    public long getBalance(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement("SELECT coins FROM sv_economy WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getLong("coins");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("EconomyManager.getBalance: " + e.getMessage());
        }
        return 0;
    }

    public void setBalance(UUID uuid, long amount) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_economy (uuid, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = VALUES(coins)")) {
            st.setString(1, uuid.toString());
            st.setLong(2, amount);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("EconomyManager.setBalance: " + e.getMessage());
        }
    }

    public boolean has(UUID uuid, long amount) {
        return getBalance(uuid) >= amount;
    }

    public boolean withdraw(UUID uuid, long amount) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE sv_economy SET coins = coins - ? WHERE uuid = ? AND coins >= ?")) {
            st.setLong(1, amount);
            st.setString(2, uuid.toString());
            st.setLong(3, amount);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("EconomyManager.withdraw: " + e.getMessage());
            return false;
        }
    }

    public void deposit(UUID uuid, long amount) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_economy (uuid, coins) VALUES (?, ?) " +
                 "ON DUPLICATE KEY UPDATE coins = coins + ?")) {
            st.setString(1, uuid.toString());
            st.setLong(2, amount);
            st.setLong(3, amount);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("EconomyManager.deposit: " + e.getMessage());
        }
        if (plugin.getAchievementManager() != null) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) plugin.getAchievementManager().checkCoins(player);
        }
    }

    public List<AbstractMap.SimpleEntry<UUID, Long>> getTopCoins(int limit) {
        List<AbstractMap.SimpleEntry<UUID, Long>> result = new ArrayList<>();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT uuid, coins FROM sv_economy ORDER BY coins DESC LIMIT ?")) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    result.add(new AbstractMap.SimpleEntry<>(
                        UUID.fromString(rs.getString("uuid")), rs.getLong("coins")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("EconomyManager.getTopCoins: " + e.getMessage());
        }
        return result;
    }
}
