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
import java.util.UUID;

public class HomeManager {

    private final PHSurvival plugin;
    private File dataFile;
    private YamlConfiguration data;
    // Map: UUID -> (homeName -> Location)
    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();

    public HomeManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    public void setHome(UUID uuid, String name, Location location) {
        homes.computeIfAbsent(uuid, k -> new HashMap<>()).put(name.toLowerCase(), location);
        saveHome(uuid, name.toLowerCase(), location);
    }

    public Location getHome(UUID uuid, String name) {
        Map<String, Location> playerHomes = homes.get(uuid);
        if (playerHomes == null) return null;
        return playerHomes.get(name.toLowerCase());
    }

    public boolean deleteHome(UUID uuid, String name) {
        Map<String, Location> playerHomes = homes.get(uuid);
        if (playerHomes == null || !playerHomes.containsKey(name.toLowerCase())) return false;
        playerHomes.remove(name.toLowerCase());
        data.set("homes." + uuid + "." + name.toLowerCase(), null);
        saveToDisk();
        return true;
    }

    public Map<String, Location> getHomes(UUID uuid) {
        return homes.getOrDefault(uuid, new HashMap<>());
    }

    public int getHomeCount(UUID uuid) {
        return homes.getOrDefault(uuid, new HashMap<>()).size();
    }

    private void saveHome(UUID uuid, String name, Location loc) {
        String path = "homes." + uuid + "." + name;
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", loc.getYaw());
        data.set(path + ".pitch", loc.getPitch());
        saveToDisk();
    }

    private void saveToDisk() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Homes konnten nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "homes.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.contains("homes")) return;
        for (String uuidStr : data.getConfigurationSection("homes").getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<String, Location> playerHomes = new HashMap<>();
            for (String homeName : data.getConfigurationSection("homes." + uuidStr).getKeys(false)) {
                String path = "homes." + uuidStr + "." + homeName;
                String worldName = data.getString(path + ".world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                double x = data.getDouble(path + ".x");
                double y = data.getDouble(path + ".y");
                double z = data.getDouble(path + ".z");
                float yaw = (float) data.getDouble(path + ".yaw");
                float pitch = (float) data.getDouble(path + ".pitch");
                playerHomes.put(homeName, new Location(world, x, y, z, yaw, pitch));
            }
            if (!playerHomes.isEmpty()) {
                homes.put(uuid, playerHomes);
            }
        }
    }
}
