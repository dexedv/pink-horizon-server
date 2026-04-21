package de.pinkhorizon.minigames.managers;

import de.pinkhorizon.minigames.PHMinigames;
import de.pinkhorizon.minigames.bedwars.BedWarsArenaConfig;
import de.pinkhorizon.minigames.bedwars.BedWarsGame;
import de.pinkhorizon.minigames.bedwars.BedWarsTeamColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class BedWarsArenaManager {

    private final PHMinigames                   plugin;
    private final Map<String, BedWarsArenaConfig> arenas = new HashMap<>();
    private final List<BedWarsGame>             activeGames = new ArrayList<>();

    public BedWarsArenaManager(PHMinigames plugin) {
        this.plugin = plugin;
        loadArenas();
    }

    // ── Laden ─────────────────────────────────────────────────────────────

    public void loadArenas() {
        arenas.clear();
        try (Connection con = plugin.getDb().getConnection()) {
            // Arenen
            try (ResultSet rs = con.createStatement()
                    .executeQuery("SELECT id, name, world, max_teams, team_size FROM mg_bedwars_arenas")) {
                while (rs.next()) {
                    BedWarsArenaConfig cfg = new BedWarsArenaConfig(
                            rs.getInt("id"), rs.getString("name"),
                            rs.getString("world"), rs.getInt("max_teams"), rs.getInt("team_size"));
                    arenas.put(cfg.name, cfg);
                }
            }

            // Spawns
            try (ResultSet rs = con.createStatement()
                    .executeQuery("SELECT arena_id, team, x, y, z, yaw, pitch FROM mg_bedwars_spawns")) {
                while (rs.next()) {
                    BedWarsArenaConfig cfg = getById(rs.getInt("arena_id"));
                    if (cfg == null) continue;
                    BedWarsTeamColor color = BedWarsTeamColor.fromString(rs.getString("team"));
                    if (color == null) continue;
                    double x = rs.getDouble("x"), y = rs.getDouble("y"), z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw"), pitch = rs.getFloat("pitch");
                    cfg.teamSpawnData.put(color, new double[]{x, y, z, yaw, pitch});
                    World world = Bukkit.getWorld(cfg.world);
                    if (world != null) cfg.teamSpawns.put(color, new Location(world, x, y, z, yaw, pitch));
                }
            }

            // Betten
            try (ResultSet rs = con.createStatement()
                    .executeQuery("SELECT arena_id, team, x, y, z FROM mg_bedwars_beds")) {
                while (rs.next()) {
                    BedWarsArenaConfig cfg = getById(rs.getInt("arena_id"));
                    if (cfg == null) continue;
                    BedWarsTeamColor color = BedWarsTeamColor.fromString(rs.getString("team"));
                    if (color == null) continue;
                    cfg.bedBlocks.put(color, new int[]{rs.getInt("x"), rs.getInt("y"), rs.getInt("z")});
                }
            }

            // Spawner
            try (ResultSet rs = con.createStatement()
                    .executeQuery("SELECT id, arena_id, type, x, y, z FROM mg_bedwars_spawners")) {
                while (rs.next()) {
                    BedWarsArenaConfig cfg = getById(rs.getInt("arena_id"));
                    if (cfg == null) continue;
                    cfg.spawners.add(new BedWarsArenaConfig.SpawnerEntry(
                            rs.getInt("id"), rs.getString("type"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden der Arenen", e);
        }
    }

    // ── Erstellen / Konfigurieren ──────────────────────────────────────────

    public boolean createArena(String name, String world, int maxTeams, int teamSize) {
        try (Connection con = plugin.getDb().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO mg_bedwars_arenas (name, world, max_teams, team_size) VALUES (?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, world);
            ps.setInt(3, maxTeams);
            ps.setInt(4, teamSize);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    BedWarsArenaConfig cfg = new BedWarsArenaConfig(keys.getInt(1), name, world, maxTeams, teamSize);
                    arenas.put(name, cfg);
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen der Arena " + name, e);
        }
        return false;
    }

    public boolean setSpawn(String arenaName, BedWarsTeamColor team, Location loc) {
        BedWarsArenaConfig cfg = arenas.get(arenaName);
        if (cfg == null) return false;
        try (Connection con = plugin.getDb().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "REPLACE INTO mg_bedwars_spawns (arena_id, team, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?)")) {
            ps.setInt(1, cfg.id);
            ps.setString(2, team.name());
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setFloat(6, loc.getYaw());
            ps.setFloat(7, loc.getPitch());
            ps.executeUpdate();
            cfg.teamSpawnData.put(team, new double[]{loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()});
            cfg.teamSpawns.put(team, loc.clone());
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "setSpawn Fehler", e);
            return false;
        }
    }

    public boolean setBed(String arenaName, BedWarsTeamColor team, int x, int y, int z) {
        BedWarsArenaConfig cfg = arenas.get(arenaName);
        if (cfg == null) return false;
        try (Connection con = plugin.getDb().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "REPLACE INTO mg_bedwars_beds (arena_id, team, x, y, z, world) VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, cfg.id);
            ps.setString(2, team.name());
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            ps.setString(6, cfg.world);
            ps.executeUpdate();
            cfg.bedBlocks.put(team, new int[]{x, y, z});
            cfg.bedFootBlockData.remove(team);
            cfg.bedHeadBlockData.remove(team);
            cfg.bedHeadBlocks.remove(team);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "setBed Fehler", e);
            return false;
        }
    }

    public boolean addSpawner(String arenaName, String type, double x, double y, double z) {
        BedWarsArenaConfig cfg = arenas.get(arenaName);
        if (cfg == null) return false;
        try (Connection con = plugin.getDb().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO mg_bedwars_spawners (arena_id, type, x, y, z) VALUES (?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cfg.id);
            ps.setString(2, type.toUpperCase());
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    cfg.spawners.add(new BedWarsArenaConfig.SpawnerEntry(
                            keys.getInt(1), type.toUpperCase(), x, y, z));
                }
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "addSpawner Fehler", e);
            return false;
        }
    }

    // ── Spiele verwalten ──────────────────────────────────────────────────

    public BedWarsGame findOrCreateGame(String arenaName) {
        BedWarsArenaConfig cfg = arenas.get(arenaName);
        if (cfg == null) return null;

        for (BedWarsGame game : activeGames) {
            if (game.getArena().name.equals(arenaName)
                    && (game.getState() == BedWarsGame.GameState.WAITING
                     || game.getState() == BedWarsGame.GameState.STARTING)) {
                return game;
            }
        }
        BedWarsGame game = new BedWarsGame(plugin, cfg);
        activeGames.add(game);
        return game;
    }

    public BedWarsGame findOrCreateAnyGame() {
        for (BedWarsArenaConfig cfg : arenas.values()) {
            if (cfg.isFullyConfigured()) {
                return findOrCreateGame(cfg.name);
            }
        }
        return null;
    }

    public BedWarsGame getGameOf(UUID player) {
        for (BedWarsGame game : activeGames) {
            if (game.isInGame(player)) return game;
        }
        return null;
    }

    public void gameEnded(BedWarsGame game) {
        activeGames.remove(game);
    }

    public void stopAll() {
        for (BedWarsGame game : new ArrayList<>(activeGames)) {
            for (UUID uuid : new java.util.HashSet<>(game.getAllPlayers())) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(uuid);
                if (p != null) game.removePlayer(p, true);
            }
        }
        activeGames.clear();
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public Collection<BedWarsArenaConfig> getArenas()             { return arenas.values(); }
    public BedWarsArenaConfig             getArena(String name)   { return arenas.get(name); }
    public List<BedWarsGame>              getActiveGames()        { return activeGames; }

    private BedWarsArenaConfig getById(int id) {
        return arenas.values().stream().filter(c -> c.id == id).findFirst().orElse(null);
    }
}
