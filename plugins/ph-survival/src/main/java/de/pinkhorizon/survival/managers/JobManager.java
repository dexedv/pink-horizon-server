package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.*;
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
        FISHER    ("Fischer",     "§7Angle Fische für Coins.",        Material.FISHING_ROD,     TextColor.color(0x1ABC9C)),
        BREWER    ("Brauer",      "§7Braue Tränke für Coins.",        Material.BREWING_STAND,   TextColor.color(0x9B59B6)),
        BUILDER   ("Baumeister",  "§7Baue Strukturen für Coins.",     Material.BRICKS,          TextColor.color(0xE67E22));

        public final String    displayName;
        public final String    description;
        public final Material  icon;
        public final TextColor color;

        Job(String displayName, String description, Material icon, TextColor color) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }
    }

    // ── Level-System ─────────────────────────────────────────────────────

    // 100 Levels; XP_THRESHOLDS[i] = XP benötigt um von Level i auf i+1 zu kommen
    // Formel: 400 * i^2  →  Level 10 ≈ 9 h, Level 100 ist Prestige-Ziel
    private static final int[] XP_THRESHOLDS = new int[100];
    static {
        for (int i = 1; i < 100; i++) {
            XP_THRESHOLDS[i] = 400 * i * i;
        }
    }

    public static int    getMaxLevel()            { return XP_THRESHOLDS.length; }
    public static double getMultiplier(int level) { return 1.0 + (level - 1) * 0.05; }

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
    // Lazy-loaded cache
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public JobManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    // ── Public API ───────────────────────────────────────────────────────

    public Job getJob(UUID uuid) { return get(uuid).job; }

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
        persistJob(player.getUniqueId(), pd);
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
        persistJobProgress(player.getUniqueId(), pd.job, jp);
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
            if (plugin.getAchievementManager() != null)
                plugin.getAchievementManager().checkJobLevel(player, jp.level);
            return true;
        }
        return false;
    }

    private PlayerData get(UUID uuid) {
        return players.computeIfAbsent(uuid, this::loadPlayer);
    }

    private PlayerData loadPlayer(UUID uuid) {
        PlayerData pd = new PlayerData();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT job_id, level, xp, active FROM sv_jobs WHERE uuid=?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    try {
                        Job         j  = Job.valueOf(rs.getString("job_id"));
                        JobProgress jp = pd.getProgress(j);
                        jp.level = rs.getInt("level");
                        jp.xp    = rs.getInt("xp");
                        if (rs.getBoolean("active")) pd.job = j;
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("JobManager.loadPlayer: " + e.getMessage());
        }
        return pd;
    }

    private void persistJob(UUID uuid, PlayerData pd) {
        try (Connection c = con()) {
            // Clear all active flags for this player
            try (PreparedStatement st = c.prepareStatement(
                     "UPDATE sv_jobs SET active=FALSE WHERE uuid=?")) {
                st.setString(1, uuid.toString());
                st.executeUpdate();
            }
            if (pd.job != null) {
                JobProgress jp = pd.getProgress(pd.job);
                try (PreparedStatement st = c.prepareStatement(
                         "INSERT INTO sv_jobs (uuid, job_id, level, xp, active) VALUES (?,?,?,?,TRUE)" +
                         " ON DUPLICATE KEY UPDATE active=TRUE, level=VALUES(level), xp=VALUES(xp)")) {
                    st.setString(1, uuid.toString());
                    st.setString(2, pd.job.name());
                    st.setInt(3, jp.level);
                    st.setInt(4, jp.xp);
                    st.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("JobManager.persistJob: " + e.getMessage());
        }
    }

    private void persistJobProgress(UUID uuid, Job job, JobProgress jp) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_jobs (uuid, job_id, level, xp, active) VALUES (?,?,?,?,TRUE)" +
                 " ON DUPLICATE KEY UPDATE level=VALUES(level), xp=VALUES(xp)")) {
            st.setString(1, uuid.toString());
            st.setString(2, job.name());
            st.setInt(3, jp.level);
            st.setInt(4, jp.xp);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("JobManager.persistJobProgress: " + e.getMessage());
        }
    }
}
