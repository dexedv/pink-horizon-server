package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SurvivalHologramManager {

    private static final double LINE_SPACING = 0.28;

    private final PHSurvival plugin;
    private final File dataFile;
    private final YamlConfiguration data;
    // name -> Liste der gespawnten Entities (eine pro Zeile)
    private final Map<String, List<TextDisplay>> active = new HashMap<>();

    public SurvivalHologramManager(PHSurvival plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "holograms.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void spawnAll() {
        active.values().forEach(list -> list.forEach(e -> { if (e != null && !e.isDead()) e.remove(); }));
        active.clear();

        if (!data.contains("holograms")) return;
        for (String name : data.getConfigurationSection("holograms").getKeys(false)) {
            String path = "holograms." + name;
            String worldName = data.getString(path + ".world", "world");
            double x = data.getDouble(path + ".x");
            double y = data.getDouble(path + ".y");
            double z = data.getDouble(path + ".z");
            float scale = (float) data.getDouble(path + ".scale", 1.0);
            List<String> lines = data.getStringList(path + ".lines");

            World world = plugin.getServer().getWorld(worldName);
            if (world == null) continue;
            spawnLines(name, new Location(world, x, y, z), lines, scale);
        }
        plugin.getLogger().info(active.size() + " Hologram(e) gespawnt.");
    }

    public void create(String name, Location base, List<String> lines, float scale) {
        remove(name);
        String path = "holograms." + name;
        data.set(path + ".world", base.getWorld().getName());
        data.set(path + ".x", base.getX());
        data.set(path + ".y", base.getY());
        data.set(path + ".z", base.getZ());
        data.set(path + ".scale", scale);
        data.set(path + ".lines", lines);
        save();
        spawnLines(name, base, lines, scale);
    }

    public boolean remove(String name) {
        List<TextDisplay> entities = active.remove(name);
        if (entities != null) entities.forEach(e -> { if (!e.isDead()) e.remove(); });
        if (!data.contains("holograms." + name)) return false;
        data.set("holograms." + name, null);
        save();
        return true;
    }

    public Map<String, List<TextDisplay>> getAll() { return active; }

    private void spawnLines(String name, Location base, List<String> lines, float scale) {
        List<TextDisplay> entities = new ArrayList<>();
        // Erste Zeile oben, jede weitere 0.28 * scale Blöcke tiefer
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
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 0, 1)
                ));
            });
            entities.add(display);
        }
        active.put(name, entities);
    }

    private void save() {
        try { data.save(dataFile); } catch (IOException e) {
            plugin.getLogger().warning("holograms.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
