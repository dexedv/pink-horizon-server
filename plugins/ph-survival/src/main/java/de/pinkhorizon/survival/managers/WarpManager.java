package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class WarpManager {

    private final PHSurvival plugin;
    private final Map<String, Location> warps = new HashMap<>();

    public WarpManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    public void setWarp(String name, Location loc) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_warps (name, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?)" +
                 " ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z)," +
                 " yaw=VALUES(yaw), pitch=VALUES(pitch)")) {
            st.setString(1, name.toLowerCase());
            st.setString(2, loc.getWorld().getName());
            st.setDouble(3, loc.getX());
            st.setDouble(4, loc.getY());
            st.setDouble(5, loc.getZ());
            st.setFloat(6, loc.getYaw());
            st.setFloat(7, loc.getPitch());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("WarpManager.setWarp: " + e.getMessage());
        }
        warps.put(name.toLowerCase(), loc);
    }

    public Location getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public boolean deleteWarp(String name) {
        if (!warps.containsKey(name.toLowerCase())) return false;
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "DELETE FROM sv_warps WHERE name=?")) {
            st.setString(1, name.toLowerCase());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("WarpManager.deleteWarp: " + e.getMessage());
        }
        warps.remove(name.toLowerCase());
        return true;
    }

    public Map<String, Location> getWarps() {
        return new HashMap<>(warps);
    }

    private void load() {
        try (Connection c = con();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, world, x, y, z, yaw, pitch FROM sv_warps")) {
            while (rs.next()) {
                var world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) continue;
                warps.put(rs.getString("name"),
                    new Location(world,
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("WarpManager.load: " + e.getMessage());
        }
    }
}
