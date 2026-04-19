package de.pinkhorizon.survival.managers;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.survival.PHSurvival;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    public void deposit(UUID uuid, long amount) {
        setBalance(uuid, getBalance(uuid) + amount);
    }
}
