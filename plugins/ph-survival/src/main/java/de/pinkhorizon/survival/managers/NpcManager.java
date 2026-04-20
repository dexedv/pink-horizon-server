package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.database.SurvivalDatabaseManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.sql.*;
import java.util.*;

public class NpcManager {

    public record NpcInfo(int id, String name, String world, String profession) {}

    private static final Villager.Type[] TYPES = Villager.Type.values();
    private static final Villager.Profession[] PROFESSIONS = Arrays.stream(Villager.Profession.values())
        .filter(p -> p != Villager.Profession.NONE && p != Villager.Profession.NITWIT)
        .toArray(Villager.Profession[]::new);
    private static final Random RANDOM = new Random();

    private final PHSurvival plugin;
    private final SurvivalDatabaseManager db;
    private final Map<Integer, UUID> spawnedEntities = new HashMap<>();

    public NpcManager(PHSurvival plugin) {
        this.plugin = plugin;
        this.db     = plugin.getSurvivalDb();
    }

    // ── Spawn ─────────────────────────────────────────────────────────────

    public void spawnAll() {
        for (UUID uid : spawnedEntities.values()) {
            var e = Bukkit.getEntity(uid);
            if (e != null) e.remove();
        }
        spawnedEntities.clear();

        String sql = "SELECT id, name, world, x, y, z, yaw FROM sv_npcs";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next())
                spawnNpc(rs.getInt("id"), rs.getString("name"), rs.getString("world"),
                    rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"));
        } catch (SQLException e) {
            plugin.getLogger().warning("[NpcManager] spawnAll: " + e.getMessage());
        }
        plugin.getLogger().info(spawnedEntities.size() + " NPC(s) gespawnt.");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateLookAt, 2L, 2L);
    }

    private void spawnNpc(int id, String name, String worldName, double x, double y, double z, float yaw) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        Villager.Type       type = TYPES[RANDOM.nextInt(TYPES.length)];
        Villager.Profession prof = PROFESSIONS[RANDOM.nextInt(PROFESSIONS.length)];
        Villager v = world.spawn(new Location(world, x, y, z, yaw, 0), Villager.class, entity -> {
            entity.customName(MiniMessage.miniMessage().deserialize(name));
            entity.setCustomNameVisible(true);
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setProfession(prof);
            entity.setVillagerType(type);
            entity.setPersistent(false);
            entity.setRemoveWhenFarAway(false);
        });
        spawnedEntities.put(id, v.getUniqueId());
    }

    private void updateLookAt() {
        for (UUID uid : spawnedEntities.values()) {
            if (!(Bukkit.getEntity(uid) instanceof Villager villager)) continue;
            Location npcLoc = villager.getLocation();
            Player nearest = null;
            double nearestSq = 144.0;
            for (Player p : villager.getWorld().getPlayers()) {
                double sq = p.getLocation().distanceSquared(npcLoc);
                if (sq < nearestSq) { nearestSq = sq; nearest = p; }
            }
            if (nearest == null) continue;
            double dx = nearest.getX() - npcLoc.getX();
            double dy = nearest.getEyeLocation().getY() - (npcLoc.getY() + 1.62);
            double dz = nearest.getZ() - npcLoc.getZ();
            villager.setRotation(
                (float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    public int createNpc(String name, Location loc, Villager.Profession profession) {
        String sql = "INSERT INTO sv_npcs (name, world, x, y, z, yaw, profession) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, loc.getWorld().getName());
            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.setFloat(6, loc.getYaw());
            stmt.setString(7, profession.name());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    spawnNpc(id, name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw());
                    return id;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NpcManager] createNpc: " + e.getMessage());
        }
        return -1;
    }

    public boolean renameNpc(int id, String name) {
        String sql = "UPDATE sv_npcs SET name=? WHERE id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setInt(2, id);
            if (stmt.executeUpdate() == 0) return false;
        } catch (SQLException e) {
            plugin.getLogger().warning("[NpcManager] renameNpc: " + e.getMessage());
            return false;
        }
        UUID uid = spawnedEntities.get(id);
        if (uid != null) {
            var e = Bukkit.getEntity(uid);
            if (e != null) e.customName(MiniMessage.miniMessage().deserialize(name));
        }
        return true;
    }

    public boolean deleteNpc(int id) {
        UUID uid = spawnedEntities.remove(id);
        if (uid != null) { var e = Bukkit.getEntity(uid); if (e != null) e.remove(); }
        String sql = "DELETE FROM sv_npcs WHERE id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[NpcManager] deleteNpc: " + e.getMessage());
            return false;
        }
    }

    public boolean addCommand(int id, String cmd) {
        if (getNpcInfo(id) == null) return false;
        String getMax = "SELECT COALESCE(MAX(idx) + 1, 0) FROM sv_npc_commands WHERE npc_id=?";
        String insert = "INSERT INTO sv_npc_commands (npc_id, idx, command) VALUES (?, ?, ?)";
        try (Connection con = db.getConnection()) {
            int nextIdx;
            try (PreparedStatement s = con.prepareStatement(getMax)) {
                s.setInt(1, id);
                try (ResultSet rs = s.executeQuery()) { nextIdx = rs.next() ? rs.getInt(1) : 0; }
            }
            try (PreparedStatement s = con.prepareStatement(insert)) {
                s.setInt(1, id); s.setInt(2, nextIdx); s.setString(3, cmd);
                s.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("[NpcManager] addCommand: " + e.getMessage());
            return false;
        }
    }

    public boolean removeCommand(int id, int index) {
        List<String> cmds = new ArrayList<>(getCommands(id));
        if (index < 1 || index > cmds.size()) return false;
        cmds.remove(index - 1);
        String del = "DELETE FROM sv_npc_commands WHERE npc_id=?";
        String ins = "INSERT INTO sv_npc_commands (npc_id, idx, command) VALUES (?, ?, ?)";
        try (Connection con = db.getConnection()) {
            try (PreparedStatement s = con.prepareStatement(del)) { s.setInt(1, id); s.executeUpdate(); }
            if (!cmds.isEmpty()) {
                try (PreparedStatement s = con.prepareStatement(ins)) {
                    for (int i = 0; i < cmds.size(); i++) {
                        s.setInt(1, id); s.setInt(2, i); s.setString(3, cmds.get(i));
                        s.addBatch();
                    }
                    s.executeBatch();
                }
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("[NpcManager] removeCommand: " + e.getMessage());
            return false;
        }
    }

    // ── Getter ────────────────────────────────────────────────────────────

    public List<String> getCommands(int id) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT command FROM sv_npc_commands WHERE npc_id=? ORDER BY idx";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(rs.getString("command"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NpcManager] getCommands: " + e.getMessage());
        }
        return result;
    }

    public NpcInfo getNpcInfo(int id) {
        String sql = "SELECT name, world, profession FROM sv_npcs WHERE id=?";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return new NpcInfo(id, rs.getString("name"), rs.getString("world"), rs.getString("profession"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NpcManager] getNpcInfo: " + e.getMessage());
        }
        return null;
    }

    public List<NpcInfo> getAllInfo() {
        List<NpcInfo> result = new ArrayList<>();
        String sql = "SELECT id, name, world, profession FROM sv_npcs ORDER BY id";
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next())
                result.add(new NpcInfo(rs.getInt("id"), rs.getString("name"),
                    rs.getString("world"), rs.getString("profession")));
        } catch (SQLException e) {
            plugin.getLogger().warning("[NpcManager] getAllInfo: " + e.getMessage());
        }
        return result;
    }

    public Integer getNpcIdByEntityUuid(UUID entityUuid) {
        for (var entry : spawnedEntities.entrySet())
            if (entry.getValue().equals(entityUuid)) return entry.getKey();
        return null;
    }

    public Set<String> getAllIds() {
        Set<String> result = new LinkedHashSet<>();
        for (var info : getAllInfo()) result.add(String.valueOf(info.id()));
        return result;
    }
}
