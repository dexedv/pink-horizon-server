package de.pinkhorizon.survival.managers;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.survival.PHSurvival;

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

    public long getBalance(UUID uuid) {
        String sql = "SELECT coins FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = PHCore.getInstance().getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("coins");
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Abrufen des Kontostands: " + e.getMessage());
        }
        return 0;
    }

    public void setBalance(UUID uuid, long amount) {
        String sql = "UPDATE players SET coins = ? WHERE uuid = ?";
        try (PreparedStatement stmt = PHCore.getInstance().getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setLong(1, amount);
            stmt.setString(2, uuid.toString());
            stmt.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Setzen des Kontostands: " + e.getMessage());
        }
    }

    public boolean withdraw(UUID uuid, long amount) {
        long balance = getBalance(uuid);
        if (balance < amount) return false;
        setBalance(uuid, balance - amount);
        return true;
    }

    public boolean has(UUID uuid, long amount) {
        return getBalance(uuid) >= amount;
    }

    public void deposit(UUID uuid, long amount) {
        setBalance(uuid, getBalance(uuid) + amount);
        // Achievement check (guard against null during startup)
        if (plugin.getAchievementManager() != null) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) plugin.getAchievementManager().checkCoins(player);
        }
    }

    /** Returns top N players ordered by coins descending. */
    public List<AbstractMap.SimpleEntry<UUID, Long>> getTopCoins(int limit) {
        String sql = "SELECT uuid, coins FROM players ORDER BY coins DESC LIMIT ?";
        List<AbstractMap.SimpleEntry<UUID, Long>> result = new ArrayList<>();
        try (PreparedStatement stmt = PHCore.getInstance().getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new AbstractMap.SimpleEntry<>(
                    UUID.fromString(rs.getString("uuid")), rs.getLong("coins")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Abrufen des Leaderboards: " + e.getMessage());
        }
        return result;
    }
}
