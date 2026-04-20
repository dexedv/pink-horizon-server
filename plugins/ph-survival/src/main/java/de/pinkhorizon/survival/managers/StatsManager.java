package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;;

public class StatsManager {

    private final PHSurvival plugin;
    private final File dataFile;
    private final YamlConfiguration data;

    public StatsManager(PHSurvival plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "stats.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    // ── Tode ────────────────────────────────────────────────────────────

    public void addDeath(UUID uuid) {
        set(uuid, "deaths", getDeaths(uuid) + 1);
    }

    public int getDeaths(UUID uuid) {
        return data.getInt(uuid + ".deaths", 0);
    }

    // ── Mob-Kills ────────────────────────────────────────────────────────

    public void addMobKill(UUID uuid) {
        set(uuid, "mob_kills", getMobKills(uuid) + 1);
    }

    public int getMobKills(UUID uuid) {
        return data.getInt(uuid + ".mob_kills", 0);
    }

    // ── Spieler-Kills ────────────────────────────────────────────────────

    public void addPlayerKill(UUID uuid) {
        set(uuid, "player_kills", getPlayerKills(uuid) + 1);
    }

    public int getPlayerKills(UUID uuid) {
        return data.getInt(uuid + ".player_kills", 0);
    }

    // ── Abgebaute Blöcke ─────────────────────────────────────────────────

    public void addBlocksBroken(UUID uuid, int count) {
        set(uuid, "blocks_broken", getBlocksBroken(uuid) + count);
    }

    public int getBlocksBroken(UUID uuid) {
        return data.getInt(uuid + ".blocks_broken", 0);
    }

    // ── Spielzeit (Minuten) ──────────────────────────────────────────────

    public void addPlaytime(UUID uuid, long minutes) {
        set(uuid, "playtime", getPlaytime(uuid) + minutes);
    }

    public long getPlaytime(UUID uuid) {
        return data.getLong(uuid + ".playtime", 0);
    }

    // ── Top-Listen (alle Spieler inkl. Offline) ──────────────────────────

    public List<Map.Entry<UUID, Integer>> getTopMobKills(int limit) {
        List<Map.Entry<UUID, Integer>> result = new ArrayList<>();
        for (String key : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                result.add(Map.entry(uuid, data.getInt(key + ".mob_kills", 0)));
            } catch (IllegalArgumentException ignored) {}
        }
        result.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return result.subList(0, Math.min(limit, result.size()));
    }

    public List<Map.Entry<UUID, Long>> getTopPlaytime(int limit) {
        List<Map.Entry<UUID, Long>> result = new ArrayList<>();
        for (String key : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                result.add(Map.entry(uuid, data.getLong(key + ".playtime", 0)));
            } catch (IllegalArgumentException ignored) {}
        }
        result.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return result.subList(0, Math.min(limit, result.size()));
    }

    // ── Intern ───────────────────────────────────────────────────────────

    private void set(UUID uuid, String key, Object value) {
        data.set(uuid + "." + key, value);
        save();
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Stats konnten nicht gespeichert werden: " + e.getMessage());
        }
    }
}
