package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spawnt und verwaltet Lobby-NPCs als immobile Villager-Entities.
 * Konfiguration in config.yml → Abschnitt "npcs".
 */
public class NpcManager {

    public static final NamespacedKey NPC_KEY     = new NamespacedKey("ph_skyblock", "skyblock_npc");
    public static final NamespacedKey NPC_TYPE_KEY = new NamespacedKey("ph_skyblock", "npc_type");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;

    /** npcType → EntityUUID */
    private final Map<String, UUID> spawnedNpcs = new HashMap<>();

    public NpcManager(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    /** Entfernt alte NPCs und spawnt neue aus der Konfiguration. */
    public void reloadNpcs() {
        removeAll();
        loadFromConfig();
    }

    /** Gibt den NPC-Typen (aus config) für eine Entity zurück, oder null. */
    public String getNpcType(Entity entity) {
        if (!(entity instanceof Villager)) return null;
        var pdc = entity.getPersistentDataContainer();
        if (!pdc.has(NPC_KEY, PersistentDataType.BYTE)) return null;
        return pdc.get(NPC_TYPE_KEY, PersistentDataType.STRING);
    }

    // ── Interne Methoden ──────────────────────────────────────────────────────

    private void loadFromConfig() {
        ConfigurationSection npcSection = plugin.getConfig().getConfigurationSection("npcs");
        if (npcSection == null) return;

        String lobbyWorldName = plugin.getConfig().getString("worlds.lobby", "world");
        World lobbyWorld = plugin.getServer().getWorld(lobbyWorldName);
        if (lobbyWorld == null) {
            plugin.getLogger().warning("NpcManager: Lobby-Welt '" + lobbyWorldName + "' nicht gefunden!");
            return;
        }

        // Erst alle alten NPCs in der Lobby-Welt entfernen
        lobbyWorld.getEntities().stream()
            .filter(e -> e instanceof Villager)
            .filter(e -> e.getPersistentDataContainer().has(NPC_KEY, PersistentDataType.BYTE))
            .forEach(Entity::remove);

        for (String npcId : npcSection.getKeys(false)) {
            ConfigurationSection cfg = npcSection.getConfigurationSection(npcId);
            if (cfg == null) continue;

            double x     = cfg.getDouble("x");
            double y     = cfg.getDouble("y");
            double z     = cfg.getDouble("z");
            float  yaw   = (float) cfg.getDouble("yaw", 0);
            String name  = cfg.getString("name", "§eNPC");
            String skin  = cfg.getString("skin", "plains");

            spawnNpc(lobbyWorld, npcId, x, y, z, yaw, name, skin);
        }
    }

    private void spawnNpc(World world, String npcId, double x, double y, double z,
                          float yaw, String name, String skinType) {
        Location loc = new Location(world, x, y, z, yaw, 0);
        Villager v = world.spawn(loc, Villager.class, villager -> {
            villager.setAI(false);
            villager.setSilent(true);
            villager.setInvulnerable(true);
            villager.setGravity(false);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);

            // Villager-Typ bestimmt das "Skin"
            try {
                villager.setVillagerType(Villager.Type.valueOf(skinType.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                villager.setVillagerType(Villager.Type.PLAINS);
            }
            villager.setProfession(Villager.Profession.NITWIT);
            villager.setVillagerLevel(1);

            // Name anzeigen
            villager.customName(MM.deserialize(legacyToMini(name)));
            villager.setCustomNameVisible(true);

            // PDC markieren
            var pdc = villager.getPersistentDataContainer();
            pdc.set(NPC_KEY,      PersistentDataType.BYTE,   (byte) 1);
            pdc.set(NPC_TYPE_KEY, PersistentDataType.STRING, npcId);
        });

        spawnedNpcs.put(npcId, v.getUniqueId());
        plugin.getLogger().info("NPC '" + npcId + "' gespawnt bei " + x + "/" + y + "/" + z);
    }

    private void removeAll() {
        spawnedNpcs.values().forEach(id -> {
            var e = plugin.getServer().getEntity(id);
            if (e != null) e.remove();
        });
        spawnedNpcs.clear();
    }

    private String legacyToMini(String s) {
        if (s == null) return "";
        return s
            .replace("§0", "<black>").replace("§1", "<dark_blue>").replace("§2", "<dark_green>")
            .replace("§3", "<dark_aqua>").replace("§4", "<dark_red>").replace("§5", "<dark_purple>")
            .replace("§6", "<gold>").replace("§7", "<gray>").replace("§8", "<dark_gray>")
            .replace("§9", "<blue>").replace("§a", "<green>").replace("§b", "<aqua>")
            .replace("§c", "<red>").replace("§d", "<light_purple>").replace("§e", "<yellow>")
            .replace("§f", "<white>").replace("§l", "<bold>").replace("§o", "<italic>")
            .replace("§r", "<reset>");
    }
}
