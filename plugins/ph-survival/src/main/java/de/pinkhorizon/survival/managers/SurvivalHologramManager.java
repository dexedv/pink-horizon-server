package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.database.SurvivalDatabaseManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.sql.*;
import java.util.*;

public class SurvivalHologramManager {

    private static final double LINE_SPACING = 0.28;

    private final PHSurvival plugin;
    private final SurvivalDatabaseManager db;
    private final Map<String, List<TextDisplay>> active = new HashMap<>();

    public SurvivalHologramManager(PHSurvival plugin) {
        this.plugin = plugin;
        this.db     = plugin.getSurvivalDb();
    }

    public void spawnAll() {
        active.values().forEach(list -> list.forEach(e -> { if (e != null && !e.isDead()) e.remove(); }));
        active.clear();

        String sql = "SELECT name, world, x, y, z, scale, `lines` FROM sv_holograms";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                World world = plugin.getServer().getWorld(rs.getString("world"));
                if (world == null) continue;
                spawnLines(rs.getString("name"),
                    new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")),
                    splitLines(rs.getString("lines")),
                    rs.getFloat("scale"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[HologramManager] spawnAll: " + e.getMessage());
        }
        plugin.getLogger().info(active.size() + " Hologram(e) gespawnt.");
    }

    public void create(String name, Location base, List<String> lines, float scale) {
        remove(name);
        String sql = "INSERT INTO sv_holograms (name, world, x, y, z, scale, `lines`) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), " +
                     "scale=VALUES(scale), `lines`=VALUES(`lines`)";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, base.getWorld().getName());
            stmt.setDouble(3, base.getX());
            stmt.setDouble(4, base.getY());
            stmt.setDouble(5, base.getZ());
            stmt.setFloat(6, scale);
            stmt.setString(7, joinLines(lines));
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[HologramManager] create: " + e.getMessage());
        }
        spawnLines(name, base, lines, scale);
    }

    public boolean remove(String name) {
        List<TextDisplay> entities = active.remove(name);
        if (entities != null) entities.forEach(e -> { if (!e.isDead()) e.remove(); });
        String sql = "DELETE FROM sv_holograms WHERE name=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, name);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[HologramManager] remove: " + e.getMessage());
            return false;
        }
    }

    public Map<String, List<TextDisplay>> getAll() { return active; }

    private void spawnLines(String name, Location base, List<String> lines, float scale) {
        List<TextDisplay> entities = new ArrayList<>();
        double offset = (lines.size() - 1) * LINE_SPACING * scale;
        for (int i = 0; i < lines.size(); i++) {
            final int idx = i;
            Location loc = base.clone().add(0, offset - i * LINE_SPACING * scale, 0);
            TextDisplay display = base.getWorld().spawn(loc, TextDisplay.class, entity -> {
                entity.text(MiniMessage.miniMessage().deserialize(lines.get(idx)));
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setDefaultBackground(false);
                entity.setShadowed(true);
                entity.setPersistent(false);
                entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(scale, scale, scale), new AxisAngle4f(0, 0, 0, 1)));
            });
            entities.add(display);
        }
        active.put(name, entities);
    }

    private static String joinLines(List<String> lines) {
        return String.join("\0", lines);
    }

    private static List<String> splitLines(String raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\0", -1)));
    }
}
