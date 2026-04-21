package de.pinkhorizon.minigames.bedwars;

import org.bukkit.Location;

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

    public final Map<BedWarsTeamColor, Location>        teamSpawns   = new HashMap<>();
    public final Map<BedWarsTeamColor, int[]>           bedBlocks    = new HashMap<>(); // [x, y, z]
    public final List<SpawnerEntry>                     spawners     = new ArrayList<>();

    public BedWarsArenaConfig(int id, String name, String world, int maxTeams, int teamSize) {
        this.id       = id;
        this.name     = name;
        this.world    = world;
        this.maxTeams = maxTeams;
        this.teamSize = teamSize;
    }

    public boolean isFullyConfigured() {
        if (teamSpawns.size() < maxTeams) return false;
        if (bedBlocks.size() < maxTeams) return false;
        return true;
    }

    public record SpawnerEntry(int id, String type, double x, double y, double z) {}
}
