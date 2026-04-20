package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    private final PHSurvival plugin;

    public HomeManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    public void setHome(UUID uuid, String name, Location loc) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_homes (uuid, name, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?)" +
                 " ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z)," +
                 " yaw=VALUES(yaw), pitch=VALUES(pitch)")) {
            st.setString(1, uuid.toString());
            st.setString(2, name.toLowerCase());
            st.setString(3, loc.getWorld().getName());
            st.setDouble(4, loc.getX());
            st.setDouble(5, loc.getY());
            st.setDouble(6, loc.getZ());
            st.setFloat(7, loc.getYaw());
            st.setFloat(8, loc.getPitch());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("HomeManager.setHome: " + e.getMessage());
        }
    }

    public Location getHome(UUID uuid, String name) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT world, x, y, z, yaw, pitch FROM sv_homes WHERE uuid=? AND name=?")) {
            st.setString(1, uuid.toString());
            st.setString(2, name.toLowerCase());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    var world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) return null;
                    return new Location(world,
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("HomeManager.getHome: " + e.getMessage());
        }
        return null;
    }

    public boolean deleteHome(UUID uuid, String name) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "DELETE FROM sv_homes WHERE uuid=? AND name=?")) {
            st.setString(1, uuid.toString());
            st.setString(2, name.toLowerCase());
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("HomeManager.deleteHome: " + e.getMessage());
            return false;
        }
    }

    public Map<String, Location> getHomes(UUID uuid) {
        Map<String, Location> result = new HashMap<>();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT name, world, x, y, z, yaw, pitch FROM sv_homes WHERE uuid=?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    var world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) continue;
                    result.put(rs.getString("name"),
                        new Location(world,
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("HomeManager.getHomes: " + e.getMessage());
        }
        return result;
    }

    public int getHomeCount(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT COUNT(*) FROM sv_homes WHERE uuid=?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("HomeManager.getHomeCount: " + e.getMessage());
        }
        return 0;
    }
}
