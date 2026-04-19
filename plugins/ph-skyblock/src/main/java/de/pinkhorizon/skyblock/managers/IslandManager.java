package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IslandManager {

    private final PHSkyBlock plugin;
    private static final int ISLAND_DISTANCE = 500;
    // Radius um das Insel-Zentrum der beim Reset geleert wird
    private static final int RESET_RADIUS = 50;

    private final Map<UUID, Location> islandHomes = new HashMap<>();
    private final Map<UUID, Integer> islandLevels = new HashMap<>();
    private int nextIslandIndex = 0;
    private File dataFile;
    private YamlConfiguration data;
    private World skyblockWorld;

    public IslandManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        loadData();
        loadOrCreateWorld();
    }

    private void loadOrCreateWorld() {
        skyblockWorld = Bukkit.getWorld("skyblock_world");
        if (skyblockWorld == null) {
            skyblockWorld = new WorldCreator("skyblock_world")
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .generator(new VoidChunkGenerator())
                    .createWorld();
            plugin.getLogger().info("SkyBlock-Welt erstellt.");
        }
    }

    public boolean hasIsland(UUID uuid) {
        return islandHomes.containsKey(uuid);
    }

    public Location getHome(UUID uuid) {
        return islandHomes.get(uuid);
    }

    public int getLevel(UUID uuid) {
        return islandLevels.getOrDefault(uuid, 1);
    }

    public void createIsland(UUID uuid) {
        int index = nextIslandIndex++;
        int x = (index % 100) * ISLAND_DISTANCE;
        int z = (index / 100) * ISLAND_DISTANCE;

        Location islandCenter = new Location(skyblockWorld, x, 65, z);
        spawnStarterIsland(islandCenter);

        islandHomes.put(uuid, islandCenter.clone().add(0, 2, 0));
        islandLevels.put(uuid, 1);
        saveAll();
    }

    public void resetIsland(UUID uuid) {
        Location center = islandHomes.get(uuid);
        if (center == null) return;

        // Alle Blöcke im Reset-Radius löschen
        for (int x = -RESET_RADIUS; x <= RESET_RADIUS; x++) {
            for (int y = -RESET_RADIUS; y <= RESET_RADIUS; y++) {
                for (int z = -RESET_RADIUS; z <= RESET_RADIUS; z++) {
                    Block b = skyblockWorld.getBlockAt(
                            center.getBlockX() + x,
                            center.getBlockY() + y,
                            center.getBlockZ() + z
                    );
                    if (b.getType() != Material.AIR) b.setType(Material.AIR);
                }
            }
        }

        // Neu generieren (1 Tick Verzoegerung damit Chunks geladen sind)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location newCenter = center.clone().subtract(0, 2, 0);
            spawnStarterIsland(newCenter);
        }, 2L);
    }

    private void spawnStarterIsland(Location center) {
        // Boden 5x5
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block block = skyblockWorld.getBlockAt(
                        center.getBlockX() + dx,
                        center.getBlockY() - 1,
                        center.getBlockZ() + dz);
                block.setType(dx == 0 && dz == 0 ? Material.GRASS_BLOCK : Material.DIRT);
            }
        }
        // Baum-Stamm
        for (int i = 0; i < 5; i++) {
            skyblockWorld.getBlockAt(center.getBlockX(), center.getBlockY() + i, center.getBlockZ())
                    .setType(Material.OAK_LOG);
        }
        // Blätter
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 6; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 5) <= 3) {
                        Block leaf = skyblockWorld.getBlockAt(
                                center.getBlockX() + dx,
                                center.getBlockY() + dy,
                                center.getBlockZ() + dz);
                        if (leaf.getType() == Material.AIR) leaf.setType(Material.OAK_LEAVES);
                    }
                }
            }
        }
        // Lava-Eimer und Eisblock fuer Wasserquelle
        skyblockWorld.getBlockAt(center.getBlockX() + 3, center.getBlockY(), center.getBlockZ())
                .setType(Material.ICE);
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "islands.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        nextIslandIndex = data.getInt("next-index", 0);
        if (data.contains("homes")) {
            for (String uuidStr : data.getConfigurationSection("homes").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Location loc = (Location) data.get("homes." + uuidStr);
                if (loc != null) islandHomes.put(uuid, loc);
            }
        }
        if (data.contains("levels")) {
            for (String uuidStr : data.getConfigurationSection("levels").getKeys(false)) {
                islandLevels.put(UUID.fromString(uuidStr), data.getInt("levels." + uuidStr, 1));
            }
        }
    }

    public void saveAll() {
        data.set("next-index", nextIslandIndex);
        islandHomes.forEach((uuid, loc) -> data.set("homes." + uuid, loc));
        islandLevels.forEach((uuid, level) -> data.set("levels." + uuid, level));
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Islands konnten nicht gespeichert werden: " + e.getMessage());
        }
    }
}
