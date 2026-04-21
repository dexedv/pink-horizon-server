package de.pinkhorizon.minigames.managers;

import de.pinkhorizon.minigames.PHMinigames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class MinigamesHologramManager {

    private static final float LINE_SPACING = 0.28f;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHMinigames              plugin;
    private final Map<String, List<TextDisplay>> active = new HashMap<>();
    private BukkitTask updateTask;

    public MinigamesHologramManager(PHMinigames plugin) {
        this.plugin = plugin;
    }

    public void spawnAll() {
        active.values().forEach(list -> list.forEach(e -> { if (!e.isDead()) e.remove(); }));
        active.clear();

        String sql = "SELECT name, world, x, y, z, `lines` FROM mg_holograms";
        try (Connection con = plugin.getDb().getConnection();
             ResultSet rs = con.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                World world = plugin.getServer().getWorld(rs.getString("world"));
                if (world == null) continue;
                String[] lines = rs.getString("lines").split("\0", -1);
                spawnLines(rs.getString("name"),
                        new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")),
                        lines);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "spawnAll Hologramme Fehler", e);
        }

        // Leaderboard alle 5 Minuten aktualisieren
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::updateLeaderboards, 6000L, 6000L);
    }

    public void create(String name, Location loc, String[] lines) {
        remove(name);
        String joined = String.join("\0", lines);
        String sql = "INSERT INTO mg_holograms (name, world, x, y, z, `lines`) VALUES (?,?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y),"
                + " z=VALUES(z), `lines`=VALUES(`lines`)";
        try (Connection con = plugin.getDb().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, loc.getWorld().getName());
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setString(6, joined);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Hologramm-Speichern Fehler", e);
            return;
        }
        spawnLines(name, loc, lines);
    }

    public void remove(String name) {
        List<TextDisplay> entities = active.remove(name);
        if (entities != null) entities.forEach(e -> { if (!e.isDead()) e.remove(); });

        try (Connection con = plugin.getDb().getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM mg_holograms WHERE name=?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Hologramm-Löschen Fehler", e);
        }
    }

    private void spawnLines(String name, Location base, String[] lines) {
        World world = base.getWorld();
        if (world == null) return;
        List<TextDisplay> entities = new ArrayList<>();
        double offset = (lines.length - 1) * LINE_SPACING;
        for (int i = 0; i < lines.length; i++) {
            final int idx = i;
            Location loc = base.clone().add(0, offset - i * LINE_SPACING, 0);
            TextDisplay td = world.spawn(loc, TextDisplay.class, entity -> {
                entity.text(MM.deserialize(lines[idx]));
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setDefaultBackground(false);
                entity.setShadowed(true);
                entity.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(1f, 1f, 1f),
                        new AxisAngle4f(0, 0, 0, 1)));
            });
            entities.add(td);
        }
        active.put(name, entities);
    }

    private void updateLeaderboards() {
        for (Map.Entry<String, List<TextDisplay>> entry : active.entrySet()) {
            if (!entry.getKey().startsWith("bw_top")) continue;
            String[] newLines = buildLeaderboardLines();
            List<TextDisplay> displays = entry.getValue();
            for (int i = 0; i < displays.size() && i < newLines.length; i++) {
                displays.get(i).text(MM.deserialize(newLines[i]));
            }
        }
    }

    public String[] buildLeaderboardLines() {
        List<BedWarsStatsManager.TopEntry> top = plugin.getStatsManager().getTopByWins(10);
        List<String> lines = new ArrayList<>();
        lines.add("<bold><light_purple>\u2665 BedWars Rangliste \u2665</light_purple></bold>");
        lines.add("<gray> </gray>");
        for (int i = 0; i < top.size(); i++) {
            BedWarsStatsManager.TopEntry e = top.get(i);
            String medal = switch (i) {
                case 0 -> "<gold>#1";
                case 1 -> "<gray>#2";
                case 2 -> "<red>#3";
                default -> "<dark_gray>#" + (i + 1);
            };
            lines.add(medal + " <white>" + e.name() + "</white> <dark_gray>│</dark_gray> <green>"
                    + e.wins() + "W</green> <dark_gray>│</dark_gray> <yellow>" + e.kills() + "K</yellow>");
        }
        lines.add("<gray> </gray>");
        lines.add("<gray>pinkhorizon.de</gray>");
        return lines.toArray(new String[0]);
    }

    public void stopAll() {
        if (updateTask != null) updateTask.cancel();
        active.values().forEach(list -> list.forEach(e -> { if (!e.isDead()) e.remove(); }));
        active.clear();
    }
}
