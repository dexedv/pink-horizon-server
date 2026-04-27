package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Verwaltet frei platzierbare Info-Hologramme (TextDisplay-Entities).
 * Werden in holograms.yml gespeichert und via /isadmin holo verwaltet.
 * Jedes Hologramm besteht aus einer oder mehreren Zeilen.
 */
public class InfoHologramManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Abstand zwischen den Hologramm-Zeilen */
    private static final double LINE_HEIGHT = 0.3;

    private final PHSkyBlock plugin;
    private final File holoFile;
    private FileConfiguration holoConfig;

    /** holoId → Liste der TextDisplay-UUIDs (eine pro Zeile, oben = Index 0) */
    private final Map<String, List<UUID>> holograms = new HashMap<>();

    public InfoHologramManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        this.holoFile = new File(plugin.getDataFolder(), "holograms.yml");
        load();
    }

    // ── Laden / Speichern ─────────────────────────────────────────────────────

    private void load() {
        if (!holoFile.exists()) {
            holoConfig = new YamlConfiguration();
            return;
        }
        holoConfig = YamlConfiguration.loadConfiguration(holoFile);
    }

    private void save() {
        try {
            holoConfig.save(holoFile);
        } catch (IOException e) {
            plugin.getLogger().warning("holograms.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Spawnt alle gespeicherten Hologramme neu (beim Start / Reload). */
    public void reloadAll() {
        removeAll();
        load();
        if (!holoConfig.contains("holograms")) return;

        for (String id : holoConfig.getConfigurationSection("holograms").getKeys(false)) {
            String path  = "holograms." + id;
            String wName = holoConfig.getString(path + ".world", "world");
            World world  = plugin.getServer().getWorld(wName);
            if (world == null) continue;

            double x = holoConfig.getDouble(path + ".x");
            double y = holoConfig.getDouble(path + ".y");
            double z = holoConfig.getDouble(path + ".z");
            List<String> lines = holoConfig.getStringList(path + ".lines");

            spawnLines(id, new Location(world, x, y, z), lines);
        }
        plugin.getLogger().info("InfoHologramManager: " + holograms.size() + " Hologramm(e) geladen.");
    }

    // ── Öffentliche API ───────────────────────────────────────────────────────

    /**
     * Erstellt ein neues Hologramm an der gegebenen Position.
     * Bei gleicher ID wird das alte überschrieben.
     */
    public boolean addHologram(String id, Location loc, String... lines) {
        if (loc.getWorld() == null || lines.length == 0) return false;
        removeHologram(id);

        spawnLines(id, loc, Arrays.asList(lines));

        // In YAML speichern
        String path = "holograms." + id;
        npcConfig().set(path + ".world", loc.getWorld().getName());
        npcConfig().set(path + ".x", loc.getX());
        npcConfig().set(path + ".y", loc.getY());
        npcConfig().set(path + ".z", loc.getZ());
        npcConfig().set(path + ".lines", Arrays.asList(lines));
        save();
        return true;
    }

    /** Fügt eine zusätzliche Zeile unterhalb aller bestehenden Zeilen hinzu. */
    public boolean addLine(String id, String text) {
        String path = "holograms." + id;
        if (!holoConfig.contains(path)) return false;

        List<String> lines = new ArrayList<>(holoConfig.getStringList(path + ".lines"));
        lines.add(text);
        holoConfig.set(path + ".lines", lines);
        save();

        // Neu spawnen
        String wName = holoConfig.getString(path + ".world", "world");
        World world  = plugin.getServer().getWorld(wName);
        if (world == null) return false;
        double x = holoConfig.getDouble(path + ".x");
        double y = holoConfig.getDouble(path + ".y");
        double z = holoConfig.getDouble(path + ".z");
        removeHologramEntities(id);
        spawnLines(id, new Location(world, x, y, z), lines);
        return true;
    }

    /** Entfernt eine Zeile aus einem Hologramm (0-basierter Index). */
    public boolean removeLine(String id, int index) {
        String path = "holograms." + id;
        if (!holoConfig.contains(path)) return false;

        List<String> lines = new ArrayList<>(holoConfig.getStringList(path + ".lines"));
        if (index < 0 || index >= lines.size()) return false;
        lines.remove(index);
        holoConfig.set(path + ".lines", lines);
        save();

        String wName = holoConfig.getString(path + ".world", "world");
        World world  = plugin.getServer().getWorld(wName);
        if (world == null) return false;
        double x = holoConfig.getDouble(path + ".x");
        double y = holoConfig.getDouble(path + ".y");
        double z = holoConfig.getDouble(path + ".z");
        removeHologramEntities(id);
        if (!lines.isEmpty()) spawnLines(id, new Location(world, x, y, z), lines);
        return true;
    }

    /** Löscht ein komplettes Hologramm. */
    public boolean removeHologram(String id) {
        removeHologramEntities(id);
        if (!holoConfig.contains("holograms." + id)) return false;
        holoConfig.set("holograms." + id, null);
        save();
        return true;
    }

    /** Gibt alle Hologramm-IDs zurück. */
    public List<String> listIds() {
        if (!holoConfig.contains("holograms")) return List.of();
        return new ArrayList<>(holoConfig.getConfigurationSection("holograms").getKeys(false));
    }

    /** Gibt die Zeilen eines Hologramms zurück. */
    public List<String> getLines(String id) {
        return holoConfig.getStringList("holograms." + id + ".lines");
    }

    /** Entfernt alle Info-Hologramme (beim Plugin-Stop). */
    public void removeAll() {
        holograms.values().forEach(uids -> uids.forEach(uid -> {
            var e = plugin.getServer().getEntity(uid);
            if (e != null) e.remove();
        }));
        holograms.clear();
    }

    // ── Intern ────────────────────────────────────────────────────────────────

    private void spawnLines(String id, Location base, List<String> lines) {
        List<UUID> uids = new ArrayList<>();
        // Zeilen von oben nach unten: Zeile 0 ist ganz oben
        double topY = base.getY() + (lines.size() - 1) * LINE_HEIGHT;
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = base.clone();
            lineLoc.setY(topY - i * LINE_HEIGHT);
            UUID uid = spawnLine(lineLoc, lines.get(i));
            if (uid != null) uids.add(uid);
        }
        holograms.put(id, uids);
    }

    private UUID spawnLine(Location loc, String legacyText) {
        if (loc.getWorld() == null) return null;
        String mini = legacyToMini(legacyText);
        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(MM.deserialize(mini));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setDefaultBackground(false);
            d.setShadowed(true);
        });
        return td.getUniqueId();
    }

    private void removeHologramEntities(String id) {
        List<UUID> uids = holograms.remove(id);
        if (uids == null) return;
        uids.forEach(uid -> {
            var e = plugin.getServer().getEntity(uid);
            if (e != null) e.remove();
        });
    }

    private FileConfiguration npcConfig() { return holoConfig; }

    private String legacyToMini(String s) {
        if (s == null) return "";
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
