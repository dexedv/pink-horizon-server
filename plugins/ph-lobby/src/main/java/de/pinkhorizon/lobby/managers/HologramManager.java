package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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
import java.util.HashMap;
import java.util.Map;

public class HologramManager {

    private final PHLobby plugin;
    private final File dataFile;
    private final YamlConfiguration data;

    // name -> aktive Entity
    private final Map<String, TextDisplay> active = new HashMap<>();

    public HologramManager(PHLobby plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "holograms.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    /** Alle gespeicherten Holograms spawnen (nach Entity-Cleanup aufrufen!) */
    public void spawnAll() {
        active.values().forEach(e -> { if (e != null && !e.isDead()) e.remove(); });
        active.clear();

        if (!data.contains("holograms")) return;
        for (String name : data.getConfigurationSection("holograms").getKeys(false)) {
            String path = "holograms." + name;
            String worldName = data.getString(path + ".world", "world");
            double x = data.getDouble(path + ".x");
            double y = data.getDouble(path + ".y");
            double z = data.getDouble(path + ".z");
            String text = data.getString(path + ".text", name);
            float scale = (float) data.getDouble(path + ".scale", 3.0);

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            spawnEntity(name, new Location(world, x, y, z), text, scale);
        }
        plugin.getLogger().info(active.size() + " Hologram(e) gespawnt.");
    }

    public boolean create(String name, Location loc, String text, float scale) {
        // Altes entfernen falls vorhanden
        remove(name);

        String path = "holograms." + name;
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".text", text);
        data.set(path + ".scale", scale);
        save();

        spawnEntity(name, loc, text, scale);
        return true;
    }

    public boolean remove(String name) {
        TextDisplay entity = active.remove(name);
        if (entity != null && !entity.isDead()) entity.remove();
        if (!data.contains("holograms." + name)) return false;
        data.set("holograms." + name, null);
        save();
        return true;
    }

    public Map<String, TextDisplay> getAll() {
        return active;
    }

    private void spawnEntity(String name, Location loc, String text, float scale) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, entity -> {
            entity.text(MiniMessage.miniMessage().deserialize(text));
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
        active.put(name, display);
    }

    private void save() {
        try { data.save(dataFile); } catch (IOException e) {
            plugin.getLogger().warning("holograms.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
