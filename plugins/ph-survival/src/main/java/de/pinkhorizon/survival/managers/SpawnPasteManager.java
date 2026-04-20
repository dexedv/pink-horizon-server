package de.pinkhorizon.survival.managers;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.util.Set;

public class SpawnPasteManager {

    /** Materials die als "Boden" gelten (Luft/Wasser/Pflanzen werden übersprungen). */
    private static final Set<Material> NON_SOLID = Set.of(
        Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
        Material.WATER, Material.LAVA,
        Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
        Material.DEAD_BUSH, Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID,
        Material.ALLIUM, Material.AZURE_BLUET, Material.OXEYE_DAISY,
        Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.ORANGE_TULIP,
        Material.PINK_TULIP, Material.RED_TULIP, Material.WHITE_TULIP,
        Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
        Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
        Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
        Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
        Material.SNOW
    );

    private final PHSurvival plugin;
    private final File pendingFile;

    public SpawnPasteManager(PHSurvival plugin) {
        this.plugin = plugin;
        pendingFile = new File(plugin.getDataFolder(), "pending_spawn_paste.yml");
    }

    /** Legt einen ausstehenden Spawn-Paste an (wird beim nächsten Start ausgeführt). */
    public void schedule(String schematicName) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("schematic", schematicName);
        try { cfg.save(pendingFile); } catch (Exception e) {
            plugin.getLogger().warning("pending_spawn_paste.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Prüft beim Start ob ein ausstehender Paste existiert und führt ihn aus. */
    public void checkAndExecute() {
        if (!pendingFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(pendingFile);
        String schematicName = cfg.getString("schematic");
        if (schematicName == null || schematicName.isEmpty()) { pendingFile.delete(); return; }

        // 40 Ticks warten damit alle Welten vollständig geladen sind
        Bukkit.getScheduler().runTaskLater(plugin, () -> executeAsync(schematicName), 40L);
    }

    private void executeAsync(String schematicName) {
        File schematicFile = findSchematic(schematicName);
        if (schematicFile == null) {
            plugin.getLogger().warning("[SpawnPaste] Schematic '" + schematicName + "' nicht gefunden! Abgebrochen.");
            pendingFile.delete();
            return;
        }

        World world = Bukkit.getWorlds().get(0); // Hauptwelt
        int spawnX = world.getSpawnLocation().getBlockX();
        int spawnZ = world.getSpawnLocation().getBlockZ();
        int groundY = findGroundY(world, spawnX, spawnZ);

        plugin.getLogger().info("[SpawnPaste] Paste '" + schematicName + "' bei X=" + spawnX + " Y=" + groundY + " Z=" + spawnZ);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
                if (format == null) throw new Exception("Unbekanntes Schematic-Format");

                Clipboard clipboard;
                try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                    clipboard = reader.read();
                }

                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
                try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                    Operation op = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(spawnX, groundY, spawnZ))
                        .ignoreAirBlocks(false) // Luft in der Schematic räumt Terrain innerhalb der Bounding-Box weg
                        .build();
                    Operations.complete(op);
                    int changed = editSession.getBlockChangeCount();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().info("[SpawnPaste] Fertig! " + changed + " Blöcke gesetzt.");
                        Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                            "§6[SpawnPaste] §aSpawn-Schematic wurde erfolgreich eingefügt! §7(" + changed + " Blöcke)"));
                    });
                }
                pendingFile.delete();
            } catch (Exception e) {
                plugin.getLogger().severe("[SpawnPaste] Fehler beim Paste: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /** Findet die höchste feste Bodenebene an X/Z (ignoriert Luft, Wasser, Pflanzen). */
    private int findGroundY(World world, int x, int z) {
        int maxY = world.getMaxHeight() - 1;
        for (int y = maxY; y >= world.getMinHeight(); y--) {
            Material mat = world.getBlockAt(x, y, z).getType();
            if (!NON_SOLID.contains(mat)) {
                return y + 1; // 1 über dem ersten festen Block
            }
        }
        return 64; // Fallback
    }

    private File findSchematic(String name) {
        File dir = new File(plugin.getServer().getPluginsFolder(), "FastAsyncWorldEdit/schematics");
        for (String ext : new String[]{".schem", ".schematic", ""}) {
            File f = new File(dir, name + ext);
            if (f.exists() && f.isFile()) return f;
        }
        return null;
    }
}
