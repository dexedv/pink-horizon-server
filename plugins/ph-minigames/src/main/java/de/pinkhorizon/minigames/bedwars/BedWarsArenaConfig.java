package de.pinkhorizon.minigames.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BedWarsArenaConfig {

    public final int    id;
    public final String name;
    public final String world;
    public final int    maxTeams;
    public final int    teamSize;

    /** Raw spawn data [x,y,z,yaw,pitch] – world-independent, always populated. */
    public final Map<BedWarsTeamColor, double[]>        teamSpawnData    = new HashMap<>();
    /** Cached serialized BlockData des Bett-Fußes (transient, aus Welt befüllt). */
    public final Map<BedWarsTeamColor, String>          bedFootBlockData = new HashMap<>();
    /** Cached serialized BlockData des Bett-Kopfes (transient). */
    public final Map<BedWarsTeamColor, String>          bedHeadBlockData = new HashMap<>();
    /** Cached Kopf-Position [x,y,z] per Team (transient). */
    public final Map<BedWarsTeamColor, int[]>           bedHeadBlocks    = new HashMap<>();
    /** Cached Location objects – may be empty until world is loaded. */
    public final Map<BedWarsTeamColor, Location>        teamSpawns   = new HashMap<>();
    public final Map<BedWarsTeamColor, int[]>           bedBlocks    = new HashMap<>(); // [x, y, z]
    public final List<SpawnerEntry>                     spawners     = new ArrayList<>();

    /** Returns a fresh Location resolved at call-time. Works even if world wasn't loaded at startup. */
    public Location getSpawnLocation(BedWarsTeamColor team) {
        double[] d = teamSpawnData.get(team);
        if (d == null) return null;
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, d[0], d[1], d[2], (float) d[3], (float) d[4]);
    }

    public BedWarsArenaConfig(int id, String name, String world, int maxTeams, int teamSize) {
        this.id       = id;
        this.name     = name;
        this.world    = world;
        this.maxTeams = maxTeams;
        this.teamSize = teamSize;
    }

    /**
     * Liest Bett-BlockData aus der Welt und cached sie (Fuß + Kopf).
     * Wird beim Spielstart aufgerufen wenn die Betten noch vorhanden sind.
     */
    public void cacheBedStates() {
        World w = Bukkit.getWorld(world);
        if (w == null) return;
        for (Map.Entry<BedWarsTeamColor, int[]> entry : bedBlocks.entrySet()) {
            BedWarsTeamColor color = entry.getKey();
            if (bedFootBlockData.containsKey(color)) continue; // bereits gecacht
            int[] pos = entry.getValue();
            Block block = w.getBlockAt(pos[0], pos[1], pos[2]);
            if (!(block.getBlockData() instanceof Bed bedData)) continue;
            bedFootBlockData.put(color, block.getBlockData().getAsString());
            Block head = bedData.getPart() == Bed.Part.FOOT
                    ? block.getRelative(bedData.getFacing())
                    : block.getRelative(bedData.getFacing().getOppositeFace());
            bedHeadBlockData.put(color, head.getBlockData().getAsString());
            bedHeadBlocks.put(color, new int[]{head.getX(), head.getY(), head.getZ()});
        }
    }

    /** Stellt Fuß- und Kopf-Block eines Team-Betts physisch in der Welt wieder her. */
    public void restoreBed(BedWarsTeamColor color) {
        World w = Bukkit.getWorld(world);
        if (w == null) return;
        int[] footPos  = bedBlocks.get(color);
        String footData = bedFootBlockData.get(color);
        if (footPos == null || footData == null) return;
        w.getBlockAt(footPos[0], footPos[1], footPos[2]).setBlockData(Bukkit.createBlockData(footData));
        int[] headPos  = bedHeadBlocks.get(color);
        String headData = bedHeadBlockData.get(color);
        if (headPos != null && headData != null) {
            w.getBlockAt(headPos[0], headPos[1], headPos[2]).setBlockData(Bukkit.createBlockData(headData));
        }
    }

    public boolean isFullyConfigured() {
        if (teamSpawnData.size() < maxTeams) return false;
        if (bedBlocks.size() < maxTeams) return false;
        return true;
    }

    public record SpawnerEntry(int id, String type, double x, double y, double z) {}
}
