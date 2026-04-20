package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NpcManager {

    private final PHSurvival plugin;
    private final File dataFile;
    private final YamlConfiguration data;

    /** npcId -> UUID der gespawnten Entity */
    private final Map<Integer, UUID> spawnedEntities = new HashMap<>();

    public NpcManager(PHSurvival plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "npcs.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    // ── Spawn ─────────────────────────────────────────────────────────────

    public void spawnAll() {
        for (UUID uid : spawnedEntities.values()) {
            var e = Bukkit.getEntity(uid);
            if (e != null) e.remove();
        }
        spawnedEntities.clear();

        var section = data.getConfigurationSection("npcs");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try { spawnNpc(Integer.parseInt(key)); }
            catch (NumberFormatException ignored) {}
        }
        plugin.getLogger().info(spawnedEntities.size() + " NPC(s) gespawnt.");
    }

    private void spawnNpc(int id) {
        String path = "npcs." + id;
        World world = Bukkit.getWorld(data.getString(path + ".world", "world"));
        if (world == null) return;

        double x = data.getDouble(path + ".x");
        double y = data.getDouble(path + ".y");
        double z = data.getDouble(path + ".z");
        float yaw = (float) data.getDouble(path + ".yaw");
        String name = data.getString(path + ".name", "NPC");

        Villager.Profession prof;
        try { prof = Villager.Profession.valueOf(data.getString(path + ".profession", "NONE")); }
        catch (Exception e) { prof = Villager.Profession.NONE; }
        final Villager.Profession finalProf = prof;

        Villager v = world.spawn(new Location(world, x, y, z, yaw, 0), Villager.class, entity -> {
            entity.customName(MiniMessage.miniMessage().deserialize(name));
            entity.setCustomNameVisible(true);
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setProfession(finalProf);
            entity.setVillagerType(Villager.Type.PLAINS);
            entity.setPersistent(false);
            entity.setRemoveWhenFarAway(false);
        });
        spawnedEntities.put(id, v.getUniqueId());
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    public int createNpc(String name, Location loc, Villager.Profession profession) {
        int id = 1;
        while (data.contains("npcs." + id)) id++;

        String path = "npcs." + id;
        data.set(path + ".name", name);
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", (double) loc.getYaw());
        data.set(path + ".profession", profession.name());
        data.set(path + ".commands", new ArrayList<String>());
        save();
        spawnNpc(id);
        return id;
    }

    public boolean deleteNpc(int id) {
        if (!data.contains("npcs." + id)) return false;
        UUID uid = spawnedEntities.remove(id);
        if (uid != null) { var e = Bukkit.getEntity(uid); if (e != null) e.remove(); }
        data.set("npcs." + id, null);
        save();
        return true;
    }

    public boolean addCommand(int id, String cmd) {
        if (!data.contains("npcs." + id)) return false;
        List<String> cmds = new ArrayList<>(data.getStringList("npcs." + id + ".commands"));
        cmds.add(cmd);
        data.set("npcs." + id + ".commands", cmds);
        save();
        return true;
    }

    public boolean removeCommand(int id, int index) { // 1-basiert
        if (!data.contains("npcs." + id)) return false;
        List<String> cmds = new ArrayList<>(data.getStringList("npcs." + id + ".commands"));
        if (index < 1 || index > cmds.size()) return false;
        cmds.remove(index - 1);
        data.set("npcs." + id + ".commands", cmds);
        save();
        return true;
    }

    // ── Getter ────────────────────────────────────────────────────────────

    public List<String> getCommands(int id) {
        return data.getStringList("npcs." + id + ".commands");
    }

    public Integer getNpcIdByEntityUuid(UUID entityUuid) {
        for (var e : spawnedEntities.entrySet()) {
            if (e.getValue().equals(entityUuid)) return e.getKey();
        }
        return null;
    }

    public Set<String> getAllIds() {
        var section = data.getConfigurationSection("npcs");
        return section == null ? Set.of() : section.getKeys(false);
    }

    public YamlConfiguration getData() { return data; }

    // ── Persistenz ────────────────────────────────────────────────────────

    private void save() {
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("npcs.yml konnte nicht gespeichert werden: " + e.getMessage()); }
    }
}
