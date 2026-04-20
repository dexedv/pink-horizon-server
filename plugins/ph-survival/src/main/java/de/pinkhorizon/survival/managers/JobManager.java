package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JobManager {

    // ── Job-Definitionen ─────────────────────────────────────────────────

    public enum Job {
        MINER     ("Bergmann",    "§7Baue Erze ab für Coins.",       Material.DIAMOND_PICKAXE, TextColor.color(0x5DADEC)),
        LUMBERJACK("Holzfäller",  "§7Fälle Bäume für Coins.",        Material.IRON_AXE,        TextColor.color(0x8B5E3C)),
        FARMER    ("Bauer",       "§7Ernte Feldfrüchte für Coins.",   Material.WHEAT,           TextColor.color(0xF5C518)),
        HUNTER    ("Jäger",       "§7Töte Monster für Coins.",        Material.BOW,             TextColor.color(0xC0392B)),
        FISHER    ("Fischer",     "§7Angle Fische für Coins.",        Material.FISHING_ROD,     TextColor.color(0x1ABC9C));

        public final String displayName;
        public final String description;
        public final Material icon;
        public final TextColor color;

        Job(String displayName, String description, Material icon, TextColor color) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }
    }

    // ── Level-System ─────────────────────────────────────────────────────

    // XP für den nächsten Level (Index = aktueller Level, 1-basiert)
    private static final int[] XP_THRESHOLDS = { 0, 100, 300, 600, 1000, 1500, 2500, 4000, 6000, 10000 };

    public static int getMaxLevel() { return XP_THRESHOLDS.length; }

    /** Auszahlungsmultiplikator je Level: Level 1 = 1.0x, Level 10 = 2.35x */
    public static double getMultiplier(int level) {
        return 1.0 + (level - 1) * 0.15;
    }

    public static int xpForNextLevel(int currentLevel) {
        if (currentLevel >= getMaxLevel()) return -1;
        return XP_THRESHOLDS[currentLevel];
    }

    // ── Player-Daten ─────────────────────────────────────────────────────

    private static class JobProgress {
        int level = 1;
        int xp    = 0;
    }

    private static class PlayerData {
        Job job = null;
        final Map<Job, JobProgress> progress = new HashMap<>();

        JobProgress getProgress(Job j) {
            return progress.computeIfAbsent(j, k -> new JobProgress());
        }
    }

    private final PHSurvival plugin;
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public JobManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Public API ───────────────────────────────────────────────────────

    public Job getJob(UUID uuid) {
        return get(uuid).job;
    }

    public int getLevel(UUID uuid) {
        PlayerData pd = get(uuid);
        if (pd.job == null) return 1;
        return pd.getProgress(pd.job).level;
    }

    public int getXp(UUID uuid) {
        PlayerData pd = get(uuid);
        if (pd.job == null) return 0;
        return pd.getProgress(pd.job).xp;
    }

    public int getLevelForJob(UUID uuid, Job job) {
        return get(uuid).getProgress(job).level;
    }

    public void setJob(Player player, Job job) {
        PlayerData pd = get(player.getUniqueId());
        if (pd.job == job) {
            pd.job = null;
            player.sendMessage(Component.text("§cJob verlassen: §f" + job.displayName));
        } else {
            Job old = pd.job;
            pd.job = job;
            if (old != null)
                player.sendMessage(Component.text("§7Job gewechselt: §f" + old.displayName + " §7→ §f" + job.displayName));
            else
                player.sendMessage(Component.text("§aJob angenommen: §f" + job.displayName));
        }
        saveToDisk();
    }

    /** Zahlt Coins + gibt XP. Gibt true zurück wenn Level-Up. */
    public boolean reward(Player player, int baseCoins, int baseXp) {
        PlayerData pd = get(player.getUniqueId());
        if (pd.job == null) return false;

        JobProgress jp   = pd.getProgress(pd.job);
        double      mult = getMultiplier(jp.level);
        long        coins = Math.round(baseCoins * mult);
        int         xp    = (int) Math.round(baseXp * mult);

        plugin.getEconomyManager().deposit(player.getUniqueId(), coins);
        player.sendActionBar(Component.text(
                "§6+" + coins + " Coins §8| §a+" + xp + " XP §8[" + pd.job.displayName + " Lv." + jp.level + "]"));

        boolean levelUp = addXp(player, pd, jp, xp);
        saveToDisk();
        return levelUp;
    }

    // ── Intern ───────────────────────────────────────────────────────────

    private boolean addXp(Player player, PlayerData pd, JobProgress jp, int xp) {
        if (jp.level >= getMaxLevel()) return false;
        jp.xp += xp;
        int needed = xpForNextLevel(jp.level);
        if (jp.xp >= needed) {
            jp.xp -= needed;
            jp.level++;
            player.sendMessage(Component.text(
                    "§6§lLevel-Up! §r§aDein Job §f" + pd.job.displayName
                    + " §aist jetzt §6§lLevel " + jp.level + "§a!",
                    NamedTextColor.GREEN));
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            return true;
        }
        return false;
    }

    private PlayerData get(UUID uuid) {
        return players.computeIfAbsent(uuid, k -> new PlayerData());
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "jobs.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.contains("players")) return;
        for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerData pd = new PlayerData();
                String base = "players." + uuidStr;
                String jobStr = data.getString(base + ".job");
                if (jobStr != null && !jobStr.equals("NONE")) {
                    try { pd.job = Job.valueOf(jobStr); } catch (IllegalArgumentException ignored) {}
                }
                // Per-Job-Level laden (neues Format)
                if (data.contains(base + ".jobs")) {
                    for (Job j : Job.values()) {
                        String jp = base + ".jobs." + j.name();
                        if (data.contains(jp)) {
                            JobProgress prog = pd.getProgress(j);
                            prog.level = data.getInt(jp + ".level", 1);
                            prog.xp    = data.getInt(jp + ".xp",    0);
                        }
                    }
                } else {
                    // Altes Format (globales Level) → auf aktuellen Job übertragen
                    int oldLevel = data.getInt(base + ".level", 1);
                    int oldXp    = data.getInt(base + ".xp",    0);
                    if (pd.job != null) {
                        pd.getProgress(pd.job).level = oldLevel;
                        pd.getProgress(pd.job).xp    = oldXp;
                    }
                }
                players.put(uuid, pd);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveToDisk() {
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            String base = "players." + e.getKey();
            PlayerData pd = e.getValue();
            data.set(base + ".job", pd.job != null ? pd.job.name() : "NONE");
            for (Map.Entry<Job, JobProgress> jp : pd.progress.entrySet()) {
                String path = base + ".jobs." + jp.getKey().name();
                data.set(path + ".level", jp.getValue().level);
                data.set(path + ".xp",    jp.getValue().xp);
            }
        }
        try { data.save(dataFile); }
        catch (IOException ex) { plugin.getLogger().warning("Jobs konnten nicht gespeichert werden: " + ex.getMessage()); }
    }
}
