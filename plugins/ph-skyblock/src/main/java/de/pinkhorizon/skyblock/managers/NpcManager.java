package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet Lobby-NPCs (Villager-Entities).
 * Positionen werden in npcs.yml gespeichert und via /isadmin npc gesetzt.
 */
public class NpcManager {

    public static final NamespacedKey NPC_KEY      = new NamespacedKey("ph_skyblock", "skyblock_npc");
    public static final NamespacedKey NPC_TYPE_KEY = new NamespacedKey("ph_skyblock", "npc_type");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;
    private final File npcFile;
    private FileConfiguration npcConfig;

    /** npcId → Entity-UUID */
    private final Map<String, UUID> spawnedNpcs = new HashMap<>();

    public NpcManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        this.npcFile = new File(plugin.getDataFolder(), "npcs.yml");
        load();
    }

    // ── Laden / Speichern ─────────────────────────────────────────────────────

    private void load() {
        if (!npcFile.exists()) {
            npcConfig = new YamlConfiguration();
            return;
        }
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);
    }

    private void save() {
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().warning("npcs.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ── NPC spawnen / entfernen ───────────────────────────────────────────────

    /** Spawnt alle gespeicherten NPCs (beim Start, nach Reload). */
    public void reloadNpcs() {
        removeAll();
        load();

        if (!npcConfig.contains("npcs")) return;

        String lobbyWorldName = plugin.getConfig().getString("worlds.lobby", "world");
        World lobbyWorld = plugin.getServer().getWorld(lobbyWorldName);
        if (lobbyWorld == null) {
            plugin.getLogger().warning("NpcManager: Lobby-Welt '" + lobbyWorldName + "' nicht geladen.");
            return;
        }

        // Alte persistente NPCs in der Welt zuerst entfernen
        lobbyWorld.getEntities().stream()
            .filter(e -> e instanceof Villager)
            .filter(e -> e.getPersistentDataContainer().has(NPC_KEY, PersistentDataType.BYTE))
            .forEach(Entity::remove);

        for (String id : npcConfig.getConfigurationSection("npcs").getKeys(false)) {
            String path = "npcs." + id;
            String worldName = npcConfig.getString(path + ".world", lobbyWorldName);
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) continue;

            double x    = npcConfig.getDouble(path + ".x");
            double y    = npcConfig.getDouble(path + ".y");
            double z    = npcConfig.getDouble(path + ".z");
            float  yaw  = (float) npcConfig.getDouble(path + ".yaw", 0);
            String name = npcConfig.getString(path + ".name", "§eNPC");
            String skin = npcConfig.getString(path + ".skin", "PLAINS");

            UUID uid = spawnNpc(world, id, x, y, z, yaw, name, skin);
            if (uid != null) spawnedNpcs.put(id, uid);
        }
        plugin.getLogger().info("NpcManager: " + spawnedNpcs.size() + " NPC(s) geladen.");
    }

    /**
     * Fügt einen neuen NPC hinzu, speichert ihn und spawnt ihn sofort.
     * @param id     Eindeutiger Bezeichner (z.B. "quest-master")
     * @param loc    Exakte Position
     * @param name   Anzeigename (§-Codes erlaubt)
     * @param skin   Villager.Type als String (PLAINS, SWAMP, TAIGA, …)
     */
    public boolean addNpc(String id, Location loc, String name, String skin) {
        if (loc.getWorld() == null) return false;

        // Entity spawnen
        UUID uid = spawnNpc(loc.getWorld(), id, loc.getX(), loc.getY(), loc.getZ(),
            loc.getYaw(), name, skin);
        if (uid == null) return false;

        // Alten NPC mit gleicher ID zuerst entfernen
        removeNpc(id);

        spawnedNpcs.put(id, uid);

        // In YAML speichern
        String path = "npcs." + id;
        npcConfig.set(path + ".world", loc.getWorld().getName());
        npcConfig.set(path + ".x", loc.getX());
        npcConfig.set(path + ".y", loc.getY());
        npcConfig.set(path + ".z", loc.getZ());
        npcConfig.set(path + ".yaw", (double) loc.getYaw());
        npcConfig.set(path + ".name", name);
        npcConfig.set(path + ".skin", skin);
        save();
        return true;
    }

    /** Entfernt einen NPC nach ID (Entity + Eintrag in YAML). */
    public boolean removeNpc(String id) {
        UUID uid = spawnedNpcs.remove(id);
        if (uid != null) {
            Entity e = plugin.getServer().getEntity(uid);
            if (e != null) e.remove();
        }

        if (!npcConfig.contains("npcs." + id)) return false;
        npcConfig.set("npcs." + id, null);
        save();
        return true;
    }

    /** Gibt alle gespeicherten NPC-IDs zurück. */
    public List<String> listNpcIds() {
        if (!npcConfig.contains("npcs")) return List.of();
        return new ArrayList<>(npcConfig.getConfigurationSection("npcs").getKeys(false));
    }

    /** Gibt den NPC-Typ für eine Entity zurück, oder null. */
    public String getNpcType(Entity entity) {
        if (!(entity instanceof Villager)) return null;
        var pdc = entity.getPersistentDataContainer();
        if (!pdc.has(NPC_KEY, PersistentDataType.BYTE)) return null;
        return pdc.get(NPC_TYPE_KEY, PersistentDataType.STRING);
    }

    // ── Intern ────────────────────────────────────────────────────────────────

    private UUID spawnNpc(World world, String id, double x, double y, double z,
                          float yaw, String name, String skinType) {
        Location loc = new Location(world, x, y, z, yaw, 0);
        Villager v = world.spawn(loc, Villager.class, villager -> {
            villager.setAI(false);
            villager.setSilent(true);
            villager.setInvulnerable(true);
            villager.setGravity(false);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);

            Villager.Type vType = Villager.Type.PLAINS;
            try { vType = Villager.Type.valueOf(skinType.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
            villager.setVillagerType(vType);
            villager.setProfession(Villager.Profession.NITWIT);
            villager.setVillagerLevel(1);

            villager.customName(MM.deserialize(legacyToMini(name)));
            villager.setCustomNameVisible(true);

            var pdc = villager.getPersistentDataContainer();
            pdc.set(NPC_KEY,      PersistentDataType.BYTE,   (byte) 1);
            pdc.set(NPC_TYPE_KEY, PersistentDataType.STRING, id);
        });
        return v.getUniqueId();
    }

    private void removeAll() {
        spawnedNpcs.values().forEach(uid -> {
            Entity e = plugin.getServer().getEntity(uid);
            if (e != null) e.remove();
        });
        spawnedNpcs.clear();
    }

    private String legacyToMini(String s) {
        if (s == null) return "";
        s = s.replace('&', '§'); // & und § beide akzeptieren
        return s
            .replace("§0","<black>").replace("§1","<dark_blue>").replace("§2","<dark_green>")
            .replace("§3","<dark_aqua>").replace("§4","<dark_red>").replace("§5","<dark_purple>")
            .replace("§6","<gold>").replace("§7","<gray>").replace("§8","<dark_gray>")
            .replace("§9","<blue>").replace("§a","<green>").replace("§b","<aqua>")
            .replace("§c","<red>").replace("§d","<light_purple>").replace("§e","<yellow>")
            .replace("§f","<white>").replace("§l","<bold>").replace("§o","<italic>")
            .replace("§n","<underlined>").replace("§m","<strikethrough>").replace("§r","<reset>");
    }
}
