package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class IslandScoreManager {

    private final PHSkyBlock plugin;
    private final Map<Material, Integer> blockValues = new EnumMap<>(Material.class);

    public IslandScoreManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        loadBlockValues();
    }

    private void loadBlockValues() {
        var section = plugin.getConfig().getConfigurationSection("block-values");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat != null) blockValues.put(mat, section.getInt(key));
        }
    }

    public int getBlockValue(Material mat) {
        return blockValues.getOrDefault(mat, 0);
    }

    /**
     * Berechnet den Score einer Insel asynchron und ruft dann den Callback auf dem Main-Thread auf.
     */
    public void calculateScore(Island island, Runnable onDone) {
        World w = Bukkit.getWorld(island.getWorld());
        if (w == null) { onDone.run(); return; }

        Location center = new Location(w, island.getCenterX(), island.getCenterY(), island.getCenterZ());
        int half = island.getSize() / 2;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AtomicLong score = new AtomicLong(0);

            int minX = island.getCenterX() - half;
            int maxX = island.getCenterX() + half;
            int minZ = island.getCenterZ() - half;
            int maxZ = island.getCenterZ() + half;

            for (int x = minX; x <= maxX; x += 16) {
                for (int z = minZ; z <= maxZ; z += 16) {
                    final int fx = x, fz = z;
                    // Chunks müssen auf Main-Thread gezählt werden
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Chunk chunk = w.getChunkAt(fx >> 4, fz >> 4);
                        for (int bx = 0; bx < 16; bx++) {
                            for (int by = w.getMinHeight(); by < w.getMaxHeight(); by++) {
                                for (int bz = 0; bz < 16; bz++) {
                                    int gx = (fx & ~15) + bx;
                                    int gz = (fz & ~15) + bz;
                                    if (gx < minX || gx > maxX || gz < minZ || gz > maxZ) continue;
                                    Block b = chunk.getBlock(bx, by, bz);
                                    int val = getBlockValue(b.getType());
                                    if (val > 0) score.addAndGet(val);
                                }
                            }
                        }
                    });
                }
            }

            // Kurz warten damit Main-Thread die Chunks verarbeitet hat, dann speichern
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                long newScore = score.get();
                long newLevel = Math.max(1, (long) Math.cbrt(newScore / 10.0));
                island.setScore(newScore);
                island.setLevel(newLevel);
                plugin.getIslandManager().saveIsland(island);
                onDone.run();
            }, 10L);
        });
    }
}
