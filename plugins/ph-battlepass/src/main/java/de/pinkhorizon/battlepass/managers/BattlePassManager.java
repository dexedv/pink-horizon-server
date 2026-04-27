package de.pinkhorizon.battlepass.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.battlepass.ChallengeType;
import de.pinkhorizon.battlepass.PHBattlePass;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet den saisonalen Battle Pass:
 * - XP / Level-Tracking
 * - Tägliche, wöchentliche und saisonale Challenges
 * - Belohnungs-Vergabe
 */
public class BattlePassManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHBattlePass plugin;
    private final HikariDataSource ds;
    private final int currentSeason;
    private final int maxLevel;
    private final int xpPerLevel;

    // player-uuid → bp_xp (cache)
    private final Map<UUID, Long> xpCache     = new ConcurrentHashMap<>();
    // player-uuid → level (cache)
    private final Map<UUID, Integer> lvlCache = new ConcurrentHashMap<>();
    // player-uuid → premium (cache)
    private final Map<UUID, Boolean> premCache = new ConcurrentHashMap<>();
    // player-uuid → challenge-key → progress
    private final Map<UUID, Map<String, Integer>> challengeProgress = new ConcurrentHashMap<>();

    public BattlePassManager(PHBattlePass plugin) {
        this.plugin        = plugin;
        this.currentSeason = plugin.getConfig().getInt("current-season", 1);
        this.maxLevel      = plugin.getConfig().getInt("max-level", 100);
        this.xpPerLevel    = plugin.getConfig().getInt("xp-per-level", 1000);
        this.ds            = initPool();
        createTables();
        startAutoSave();
        startDailyReset();
    }

    // ── DB ───────────────────────────────────────────────────────────────────

    private HikariDataSource initPool() {
        var cfg = plugin.getConfig().getConfigurationSection("database");
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://"
            + cfg.getString("host", "ph-mysql") + ":" + cfg.getInt("port", 3306)
            + "/" + cfg.getString("name", "ph_skyblock") + "?useSSL=false&serverTimezone=UTC");
        hc.setUsername(cfg.getString("user", "ph_user"));
        hc.setPassword(cfg.getString("password", ""));
        hc.setMaximumPoolSize(cfg.getInt("pool-size", 4));
        hc.setPoolName("PHBattlePass-Pool");
        return new HikariDataSource(hc);
    }

    private void createTables() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_battlepass (
                    player_uuid  VARCHAR(36) NOT NULL,
                    season       INT         NOT NULL,
                    bp_xp        BIGINT      DEFAULT 0,
                    level        INT         DEFAULT 0,
                    premium      TINYINT     DEFAULT 0,
                    PRIMARY KEY (player_uuid, season)
                )
            """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_bp_challenges (
                    player_uuid    VARCHAR(36) NOT NULL,
                    season         INT         NOT NULL,
                    challenge_key  VARCHAR(64) NOT NULL,
                    progress       INT         DEFAULT 0,
                    completed      TINYINT     DEFAULT 0,
                    reset_date     DATE,
                    PRIMARY KEY (player_uuid, season, challenge_key)
                )
            """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_bp_claimed (
                    player_uuid  VARCHAR(36) NOT NULL,
                    season       INT         NOT NULL,
                    level        INT         NOT NULL,
                    premium_claimed TINYINT  DEFAULT 0,
                    PRIMARY KEY (player_uuid, season, level)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("BattlePass-Tabellen Fehler: " + e.getMessage());
        }
    }

    // ── XP & Level ──────────────────────────────────────────────────────────

    public void addBpXp(Player player, int amount, String source) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(uuid);

        long newXp = xpCache.merge(uuid, (long) amount, Long::sum);
        int newLevel = (int) Math.min(maxLevel, newXp / xpPerLevel);
        int oldLevel = lvlCache.getOrDefault(uuid, 0);

        if (newLevel > oldLevel) {
            lvlCache.put(uuid, newLevel);
            onLevelUp(player, oldLevel + 1, newLevel);
        }
    }

    private void onLevelUp(Player player, int fromLevel, int toLevel) {
        for (int lvl = fromLevel; lvl <= toLevel; lvl++) {
            player.sendMessage(MM.deserialize(
                "<gold>⭐ Battle Pass Level " + lvl + " erreicht! <gray>Belohnungen verfügbar: <white>/bp claim " + lvl));
            // Level-Up Sound
            player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    public int getLevel(UUID uuid) {
        ensureLoaded(uuid);
        return lvlCache.getOrDefault(uuid, 0);
    }

    public long getXp(UUID uuid) {
        ensureLoaded(uuid);
        return xpCache.getOrDefault(uuid, 0L);
    }

    public boolean isPremium(UUID uuid) {
        ensureLoaded(uuid);
        return premCache.getOrDefault(uuid, false);
    }

    // ── Challenges ───────────────────────────────────────────────────────────

    /**
     * Erhöht den Challenge-Fortschritt. Wird von anderen Plugins aufgerufen.
     */
    public void trackProgress(Player player, String trackingKey, int amount) {
        UUID uuid = player.getUniqueId();
        String today = LocalDate.now().toString();
        String week  = LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString();

        for (ChallengeType challenge : ChallengeType.values()) {
            if (!challenge.trackingKey.equals(trackingKey)) continue;

            String resetDate = challenge.period.equals("DAILY") ? today :
                               challenge.period.equals("WEEKLY") ? week : "SEASONAL";

            Map<String, Integer> playerProgress = challengeProgress
                .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

            String key = challenge.name() + "_" + resetDate;
            int current = playerProgress.getOrDefault(key, 0);

            // Bereits abgeschlossen?
            if (current >= challenge.targetAmount) continue;

            int newProgress = Math.min(challenge.targetAmount, current + amount);
            playerProgress.put(key, newProgress);

            if (newProgress >= challenge.targetAmount) {
                // Challenge abgeschlossen!
                completeChallengeAsync(uuid, challenge, resetDate);
                addBpXp(player, challenge.bpXp, "CHALLENGE_" + challenge.name());
                player.sendMessage(MM.deserialize(
                    "<gold>✓ Challenge abgeschlossen: <white>" + challenge.displayName
                    + " <gold>+" + challenge.bpXp + " BP-XP"));
            }
        }
    }

    private void completeChallengeAsync(UUID uuid, ChallengeType challenge, String resetDate) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sky_bp_challenges (player_uuid, season, challenge_key, progress, completed, reset_date) "
                   + "VALUES(?,?,?,?,1,?) ON DUPLICATE KEY UPDATE progress=?, completed=1")) {
                ps.setString(1, uuid.toString());
                ps.setInt   (2, currentSeason);
                ps.setString(3, challenge.name());
                ps.setInt   (4, challenge.targetAmount);
                ps.setString(5, resetDate);
                ps.setInt   (6, challenge.targetAmount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Challenge speichern fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    public void showChallenges(Player player) {
        UUID uuid    = player.getUniqueId();
        String today = LocalDate.now().toString();
        String week  = LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString();

        player.sendMessage(MM.deserialize("<gold>══ Battle Pass Challenges ══"));

        Map<String, Integer> progress = challengeProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        for (String period : List.of("DAILY", "WEEKLY", "SEASONAL")) {
            player.sendMessage(MM.deserialize(
                period.equals("DAILY") ? "<yellow>── Täglich ──" :
                period.equals("WEEKLY") ? "<green>── Wöchentlich ──" : "<light_purple>── Saisonal ──"));

            for (ChallengeType c : ChallengeType.values()) {
                if (!c.period.equals(period)) continue;
                String resetDate = period.equals("DAILY") ? today :
                                   period.equals("WEEKLY") ? week : "SEASONAL";
                String key = c.name() + "_" + resetDate;
                int prog = progress.getOrDefault(key, 0);
                boolean done = prog >= c.targetAmount;

                player.sendMessage(MM.deserialize(
                    (done ? "<dark_gray>✓ " : "<white>○ ")
                    + c.displayName
                    + " <dark_gray>[" + prog + "/" + c.targetAmount + "]"
                    + (done ? "" : " <gold>+" + c.bpXp + " BP-XP")));
            }
        }
    }

    // ── Status ───────────────────────────────────────────────────────────────

    @Deprecated
    public void showStatus(Player player) {
        UUID uuid = player.getUniqueId();
        ensureLoaded(uuid);

        int level  = lvlCache.getOrDefault(uuid, 0);
        long xp    = xpCache.getOrDefault(uuid, 0L);
        long xpForNext = (long) (level + 1) * xpPerLevel;
        boolean prem = premCache.getOrDefault(uuid, false);
        String seasonName = plugin.getConfig().getString("season-name", "Season 1");

        player.sendMessage(MM.deserialize("<gold>══ Battle Pass ══"));
        player.sendMessage(MM.deserialize("<gray>Season: <white>" + seasonName));
        player.sendMessage(MM.deserialize("<gray>Level: <white>" + level + "/" + maxLevel));
        player.sendMessage(MM.deserialize("<gray>XP: <white>" + xp + " / " + xpForNext));
        player.sendMessage(MM.deserialize("<gray>Typ: " + (prem ? "<gold>Premium" : "<gray>Gratis")));

        // XP-Balken
        int filled = (int) ((double)(xp % xpPerLevel) / xpPerLevel * 20);
        String bar = "<green>" + "█".repeat(filled) + "<dark_gray>" + "█".repeat(20 - filled);
        player.sendMessage(MM.deserialize("<gray>Fortschritt: [" + bar + "<gray>]"));
        player.sendMessage(MM.deserialize("<gray>Nutze <white>/bp challenges <gray>für aktive Aufgaben."));
    }

    // ── Belohnungen einlösen ──────────────────────────────────────────────────

    public void claimReward(Player player, int level) {
        UUID uuid = player.getUniqueId();
        if (getLevel(uuid) < level) {
            player.sendMessage(MM.deserialize("<red>Du hast Level " + level + " noch nicht erreicht!"));
            return;
        }

        // Prüfe ob bereits eingelöst
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT level FROM sky_bp_claimed WHERE player_uuid=? AND season=? AND level=?")) {
            ps.setString(1, uuid.toString());
            ps.setInt   (2, currentSeason);
            ps.setInt   (3, level);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    player.sendMessage(MM.deserialize("<red>Belohnung für Level " + level + " bereits eingelöst!"));
                    return;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Claim check fehler: " + e.getMessage());
            return;
        }

        // Belohnung geben
        ItemStack reward = getRewardForLevel(level, isPremium(uuid));
        player.getInventory().addItem(reward);

        // Als eingelöst markieren
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO sky_bp_claimed (player_uuid, season, level) VALUES(?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setInt   (2, currentSeason);
                ps.setInt   (3, level);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });

        player.sendMessage(MM.deserialize("<green>Level-" + level + "-Belohnung eingelöst!"));
    }

    private ItemStack getRewardForLevel(int level, boolean premium) {
        // Gratis-Belohnungen
        if (level % 10 == 0) {
            // Jedes 10. Level: DNA-Fragment (simuliert)
            ItemStack item = new ItemStack(Material.AMETHYST_SHARD, 1);
            ItemMeta meta  = item.getItemMeta();
            meta.displayName(MM.deserialize("<light_purple>DNA-Fragment"));
            item.setItemMeta(meta);
            return item;
        }
        if (level % 5 == 0) {
            // Jedes 5. Level: Coins-Voucher (simuliert)
            ItemStack item = new ItemStack(Material.GOLD_NUGGET, level / 5);
            ItemMeta meta  = item.getItemMeta();
            meta.displayName(MM.deserialize("<gold>" + (level * 500) + " Coins Voucher"));
            item.setItemMeta(meta);
            return item;
        }

        // Premium-Bonus
        if (premium && level % 3 == 0) {
            ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
            ItemMeta meta  = item.getItemMeta();
            meta.displayName(MM.deserialize("<gold><bold>Premium-Belohnung Lv" + level));
            item.setItemMeta(meta);
            return item;
        }

        // Standard
        return new ItemStack(Material.EXPERIENCE_BOTTLE, Math.max(1, level / 10));
    }

    public void showGui(Player player) {
        new de.pinkhorizon.battlepass.gui.BattlePassGui(plugin, player).open(player);
    }

    /** Gibt eine Kopie des Challenge-Fortschritts für einen Spieler zurück. */
    public Map<String, Integer> getChallengeProgress(UUID uuid) {
        return new java.util.LinkedHashMap<>(challengeProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    public void setPremium(Player player, boolean premium) {
        UUID uuid = player.getUniqueId();
        premCache.put(uuid, premium);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sky_battlepass (player_uuid, season, premium) VALUES(?,?,?) "
                   + "ON DUPLICATE KEY UPDATE premium=?")) {
                ps.setString(1, uuid.toString());
                ps.setInt   (2, currentSeason);
                ps.setInt   (3, premium ? 1 : 0);
                ps.setInt   (4, premium ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Premium setzen fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private void ensureLoaded(UUID uuid) {
        if (xpCache.containsKey(uuid)) return;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT bp_xp, level, premium FROM sky_battlepass WHERE player_uuid=? AND season=?")) {
            ps.setString(1, uuid.toString());
            ps.setInt   (2, currentSeason);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    xpCache.put(uuid, rs.getLong("bp_xp"));
                    lvlCache.put(uuid, rs.getInt("level"));
                    premCache.put(uuid, rs.getBoolean("premium"));
                } else {
                    xpCache.put(uuid, 0L);
                    lvlCache.put(uuid, 0);
                    premCache.put(uuid, false);
                }
            }
        } catch (SQLException e) {
            xpCache.put(uuid, 0L);
            lvlCache.put(uuid, 0);
        }
    }

    private void startAutoSave() {
        new BukkitRunnable() {
            @Override public void run() { saveAll(); }
        }.runTaskTimer(plugin, 6000L, 6000L);
    }

    private void startDailyReset() {
        // Prüft jede Stunde ob Tages-Challenges resettet werden müssen
        // (Challenges sind datum-basiert, daher nur Cache-Bereinigung nötig)
        new BukkitRunnable() {
            @Override public void run() {
                // Entferne veraltete Progress-Einträge aus Cache
                String today = LocalDate.now().toString();
                challengeProgress.values().forEach(map ->
                    map.entrySet().removeIf(e ->
                        e.getKey().contains("DAILY") && !e.getKey().contains(today)));
            }
        }.runTaskTimer(plugin, 72000L, 72000L); // alle 1h
    }

    private void saveAll() {
        xpCache.forEach((uuid, xp) -> {
            int level = lvlCache.getOrDefault(uuid, 0);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection c = ds.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO sky_battlepass (player_uuid, season, bp_xp, level) VALUES(?,?,?,?) "
                       + "ON DUPLICATE KEY UPDATE bp_xp=?, level=?")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt   (2, currentSeason);
                    ps.setLong  (3, xp);
                    ps.setInt   (4, level);
                    ps.setLong  (5, xp);
                    ps.setInt   (6, level);
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
            });
        });
    }

    public void close() {
        saveAll();
        if (ds != null && !ds.isClosed()) ds.close();
    }
}
