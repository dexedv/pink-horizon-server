package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WarpManager {

    private final PHSurvival plugin;
    private File dataFile;
    private YamlConfiguration data;
    private final Map<String, Location> warps = new HashMap<>();

    public WarpManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    public void setWarp(String name, Location location) {
        warps.put(name.toLowerCase(), location);
        String path = "warps." + name.toLowerCase();
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
        saveToDisk();
    }

    public Location getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public boolean deleteWarp(String name) {
        if (!warps.containsKey(name.toLowerCase())) return false;
        warps.remove(name.toLowerCase());
        data.set("warps." + name.toLowerCase(), null);
        saveToDisk();
        return true;
    }

    public Map<String, Location> getWarps() {
        return new HashMap<>(warps);
    }

    private void saveToDisk() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Warps konnten nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "warps.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.contains("warps")) return;
        for (String name : data.getConfigurationSection("warps").getKeys(false)) {
            String path = "warps." + name;
            String worldName = data.getString(path + ".world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            double x = data.getDouble(path + ".x");
            double y = data.getDouble(path + ".y");
            double z = data.getDouble(path + ".z");
            float yaw = (float) data.getDouble(path + ".yaw");
            float pitch = (float) data.getDouble(path + ".pitch");
            warps.put(name, new Location(world, x, y, z, yaw, pitch));
        }
    }
}
