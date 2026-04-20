package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.core.database.DatabaseManager;
import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class HologramManager {

    private final PHLobby plugin;
    private final Map<String, TextDisplay> active = new HashMap<>();

    public HologramManager(PHLobby plugin) {
        this.plugin = plugin;
    }

    private DatabaseManager db() {
        return PHCore.getInstance().getDatabaseManager();
    }

    public void spawnAll() {
        active.values().forEach(e -> { if (e != null && !e.isDead()) e.remove(); });
        active.clear();

        String sql = "SELECT name, world, x, y, z, scale, text FROM lb_holograms";
        try (Connection con = db().getConnection();
             PreparedStatement stmt = con.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) continue;
                spawnEntity(rs.getString("name"),
                    new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")),
                    rs.getString("text"),
                    rs.getFloat("scale"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[HologramManager] spawnAll: " + e.getMessage());
        }
        plugin.getLogger().info(active.size() + " Hologram(e) gespawnt.");
    }

    public boolean create(String name, Location loc, String text, float scale) {
        remove(name);
        String sql = "INSERT INTO lb_holograms (name, world, x, y, z, scale, text) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), " +
                     "scale=VALUES(scale), text=VALUES(text)";
        try (Connection con = db().getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, loc.getWorld().getName());
            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.setFloat(6, scale);
            stmt.setString(7, text);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[HologramManager] create: " + e.getMessage());
            return false;
        }
        spawnEntity(name, loc, text, scale);
        return true;
    }

    public boolean remove(String name) {
        TextDisplay entity = active.remove(name);
        if (entity != null && !entity.isDead()) entity.remove();
        String sql = "DELETE FROM lb_holograms WHERE name=?";
        try (Connection con = db().getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, name);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[HologramManager] remove: " + e.getMessage());
            return false;
        }
    }

    public Map<String, TextDisplay> getAll() { return active; }

    private void spawnEntity(String name, Location loc, String text, float scale) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, entity -> {
            entity.text(MiniMessage.miniMessage().deserialize(text));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setDefaultBackground(false);
            entity.setShadowed(true);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(scale, scale, scale), new AxisAngle4f(0, 0, 0, 1)));
        });
        active.put(name, display);
    }
}
