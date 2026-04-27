package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.inventory.ItemStack;

public class WorldManager {

    private final PHSkyBlock plugin;
    private World skyblockWorld;
    private World lobbyWorld;

    public WorldManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        loadOrCreateWorlds();
    }

    private void loadOrCreateWorlds() {
        String skyName   = plugin.getConfig().getString("worlds.skyblock", "skyblock_world");
        String lobbyName = plugin.getConfig().getString("worlds.lobby", "world");

        // Void-Welt für Inseln
        skyblockWorld = Bukkit.getWorld(skyName);
        if (skyblockWorld == null) {
            skyblockWorld = new WorldCreator(skyName)
                .generator(new de.pinkhorizon.skyblock.managers.VoidChunkGenerator())
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .createWorld();
            if (skyblockWorld != null) {
                skyblockWorld.setSpawnFlags(false, false);
                skyblockWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                skyblockWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                skyblockWorld.setTime(6000);
                plugin.getLogger().info("SkyBlock-Inselwelt '" + skyName + "' erstellt.");
            }
        }

        // Lobby-Welt laden
        lobbyWorld = Bukkit.getWorld(lobbyName);
        if (lobbyWorld == null) {
            plugin.getLogger().warning("Lobby-Welt '" + lobbyName + "' nicht gefunden!");
        }
    }

    public World getSkyblockWorld() { return skyblockWorld; }
    public World getLobbyWorld()    { return lobbyWorld; }

    /**
     * Generiert die Standard-Startinsel um das gegebene Zentrum.
     * Liefert die empfohlene Home-Position (1 Block über Zentrum).
     */
    public Location generateStarterIsland(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // ── Inselboden (7×7, oval) ─────────────────────────────────────
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 12) continue; // oval abschneiden
                Block b = w.getBlockAt(cx + dx, cy - 1, cz + dz);
                b.setType(dx == 0 && dz == 0 ? Material.GRASS_BLOCK : Material.DIRT);
            }
        }

        // ── Unterbau (Stein/Erde) ─────────────────────────────────────
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 3) continue;
                w.getBlockAt(cx + dx, cy - 2, cz + dz).setType(Material.STONE);
                w.getBlockAt(cx + dx, cy - 3, cz + dz).setType(Material.STONE);
            }
        }

        // ── Eichenbaum ────────────────────────────────────────────────
        for (int dy = 0; dy < 5; dy++) {
            w.getBlockAt(cx, cy + dy, cz).setType(Material.OAK_LOG);
        }
        // Blätter
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 5; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 4) > 3) continue;
                    Block leaf = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (leaf.getType() == Material.AIR) {
                        leaf.setType(Material.OAK_LEAVES);
                        var ld = (Leaves) leaf.getBlockData();
                        ld.setPersistent(true);
                        leaf.setBlockData(ld);
                    }
                }
            }
        }
        // Baumspitze
        w.getBlockAt(cx, cy + 6, cz).setType(Material.OAK_LEAVES);
        Block tip = w.getBlockAt(cx, cy + 6, cz);
        var ld = (Leaves) tip.getBlockData();
        ld.setPersistent(true);
        tip.setBlockData(ld);

        // ── Eisblock (Wasserquelle) ────────────────────────────────────
        w.getBlockAt(cx + 3, cy - 1, cz).setType(Material.ICE);

        // ── Lavaquelle ────────────────────────────────────────────────
        // Kleiner Basalt-Rand um Lavaboden
        w.getBlockAt(cx - 3, cy - 1, cz).setType(Material.BASALT);
        w.getBlockAt(cx - 3, cy - 1, cz - 1).setType(Material.BASALT);
        w.getBlockAt(cx - 3, cy - 1, cz + 1).setType(Material.BASALT);
        w.getBlockAt(cx - 3, cy, cz).setType(Material.LAVA);

        // ── Startkiste ────────────────────────────────────────────────
        Block chestBlock = w.getBlockAt(cx + 2, cy, cz);
        chestBlock.setType(Material.CHEST);
        if (chestBlock.getState() instanceof Chest chest) {
            var inv = chest.getInventory();
            inv.setItem(0, new ItemStack(Material.OAK_LOG, 32));
            inv.setItem(1, new ItemStack(Material.CRAFTING_TABLE));
            inv.setItem(2, new ItemStack(Material.FURNACE));
            inv.setItem(3, new ItemStack(Material.COOKED_BEEF, 16));
            inv.setItem(4, new ItemStack(Material.BONE_MEAL, 8));
            inv.setItem(5, new ItemStack(Material.SUGAR_CANE, 3));
            inv.setItem(6, new ItemStack(Material.WHEAT_SEEDS, 6));
            inv.setItem(7, new ItemStack(Material.COBBLESTONE, 16));
            inv.setItem(8, new ItemStack(Material.TORCH, 8));
        }

        // Home-Position: 1 Block über Insel-Zentrum
        return new Location(w, cx + 0.5, cy + 1, cz + 0.5, 0f, 0f);
    }

    /**
     * Löscht alle Blöcke im Bereich [center ± radius] in der Skyblock-Welt.
     */
    public void clearIslandArea(Location center, int radius) {
        World w = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                }
            }
        }
    }

}
