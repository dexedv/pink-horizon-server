package de.pinkhorizon.minigames.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

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
    public final Map<BedWarsTeamColor, double[]>        teamSpawnData = new HashMap<>();
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

    public boolean isFullyConfigured() {
        if (teamSpawnData.size() < maxTeams) return false;
        if (bedBlocks.size() < maxTeams) return false;
        return true;
    }

    public record SpawnerEntry(int id, String type, double x, double y, double z) {}
}
