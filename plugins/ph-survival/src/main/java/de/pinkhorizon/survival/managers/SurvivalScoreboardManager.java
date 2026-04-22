package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Tab-Sortierung: Scoreboard-Teams "tab_01" … "tab_07" sortieren alphabetisch →
// höchster Rang (Owner=01) erscheint in der Tabliste ganz oben.

public class SurvivalScoreboardManager {

    // ── Tab-Sort-Teams ────────────────────────────────────────────────────
    // Team-Namen sortieren alphabetisch → 01 = Owner oben, 07 = Spieler unten
    private static final String[] TAB_TEAMS =
        { "tab_01", "tab_02", "tab_03", "tab_04", "tab_05", "tab_06", "tab_07" };
    private static final Map<String, String> RANK_TO_TEAM = Map.of(
        "owner",     "tab_01",
        "admin",     "tab_02",
        "dev",       "tab_03",
        "moderator", "tab_04",
        "supporter", "tab_05",
        "vip",       "tab_06",
        "spieler",   "tab_07"
    );

    private final PHSurvival plugin;
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private BukkitTask updateTask;

    private static final String TITLE = "§a§lPink Horizon §7| §2Survival";

    // 16 einzigartige Einträge
    private static final String[] ENTRIES;
    static {
        String hex = "0123456789abcdef";
        ENTRIES = new String[16];
        for (int i = 0; i < 16; i++)
            ENTRIES[i] = "\u00a7" + hex.charAt(i) + "\u00a7r";
    }

    public SurvivalScoreboardManager(PHSurvival plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    public void giveScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("ph_surv", Criteria.DUMMY,
            Component.text(TITLE));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < 15; i++) {
            Team team = board.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            obj.getScore(ENTRIES[i]).setScore(i);
        }

        // Tab-Sort-Teams registrieren und alle aktuell online Spieler eintragen
        for (String t : TAB_TEAMS) board.registerNewTeam(t);
        for (Map.Entry<UUID, Scoreboard> e : playerBoards.entrySet()) {
            Player online = Bukkit.getPlayer(e.getKey());
            if (online != null) moveToTeam(board, online.getName(), rankTeamFor(online));
        }
        // Neuen Spieler selbst in alle bestehenden Boards eintragen
        String newTeam = rankTeamFor(player);
        for (Scoreboard b : playerBoards.values()) moveToTeam(b, player.getName(), newTeam);

        playerBoards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        refreshLines(player, board);
    }

    public void removeScoreboard(Player player) {
        // Spieler aus allen anderen Boards entfernen
        String name = player.getName();
        for (Scoreboard b : playerBoards.values()) {
            for (String t : TAB_TEAMS) {
                Team team = b.getTeam(t);
                if (team != null) team.removeEntry(name);
            }
        }
        playerBoards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Aktualisiert die Tab-Sort-Position eines Spielers in allen aktiven Boards. */
    public void updateTabSort(Player player) {
        String team = rankTeamFor(player);
        for (Scoreboard b : playerBoards.values()) moveToTeam(b, player.getName(), team);
    }

    private String rankTeamFor(Player player) {
        String rankId = plugin.getRankManager().getRank(player.getUniqueId()).id;
        return RANK_TO_TEAM.getOrDefault(rankId, "tab_07");
    }

    private void moveToTeam(Scoreboard board, String playerName, String teamName) {
        for (String t : TAB_TEAMS) {
            Team team = board.getTeam(t);
            if (team != null) team.removeEntry(playerName);
        }
        Team target = board.getTeam(teamName);
        if (target != null) target.addEntry(playerName);
    }

    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Scoreboard board = playerBoards.get(player.getUniqueId());
                if (board == null) continue;
                refreshLines(player, board);
            }
        }, 20L, 20L);
    }

    private void refreshLines(Player player, Scoreboard board) {
        UUID uuid = player.getUniqueId();

        String date   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        int online    = Bukkit.getOnlinePlayers().size();
        long coins    = plugin.getEconomyManager().getBalance(uuid);
        long bank     = plugin.getBankManager().getBalance(uuid);
        int claims    = plugin.getClaimManager().getClaimCount(uuid);
        int maxClaims = plugin.getRankManager().getMaxClaims(uuid);
        String rank   = plugin.getRankManager().getRank(uuid).chatPrefix;

        java.util.Set<UUID> friends = plugin.getFriendManager().getFriends(uuid);
        long friendsOnline = friends.stream().filter(f -> Bukkit.getPlayer(f) != null).count();

        // ── Job-Bereich ──────────────────────────────────────────────────
        JobManager.Job job = plugin.getJobManager().getJob(uuid);
        String jobNameLine, xpBarLine, bonusLine;

        if (job == null) {
            jobNameLine = "§7Job:    §8Kein Job";
            xpBarLine   = "§8Wähle einen mit §7/jobs";
            bonusLine   = "§7Freunde: §a" + friendsOnline + " §7online";
        } else {
            int level  = plugin.getJobManager().getLevel(uuid);
            int xp     = plugin.getJobManager().getXp(uuid);
            int needed = JobManager.xpForNextLevel(level);

            jobNameLine = "§7Job: §b" + job.displayName + " §7Lv.§6" + level;

            if (needed <= 0) {
                xpBarLine = "§6§lMAX LEVEL erreicht!";
            } else {
                int pct    = (int)((xp * 100L) / needed);
                int filled = pct / 10;
                xpBarLine  = "§8[§a" + "█".repeat(filled) + "§8" + "░".repeat(10 - filled) + "§8] §7" + pct + "%";
            }

            long cd = plugin.getJobBonusManager().getCooldownRemainingMs(uuid);
            bonusLine = cd <= 0
                ? "§7Bonus §8[Shift+F]§7: §a✔ Bereit"
                : "§7Bonus: §c⏳ §f" + fmtCd(cd);
        }

        // ── Freunde-Zeile (nur anzeigen wenn Job aktiv) ──────────────────
        String friendLine = job != null
            ? "§7Freunde: §a" + friendsOnline + " §7online"
            : "§aplay.pinkhorizon.de";
        String bottomLine = job != null ? "§aplay.pinkhorizon.de" : " ";

        setLine(board, 14, " ");
        setLine(board, 13, "§a§l» §fSurvival-Server");
        setLine(board, 12, "  ");
        setLine(board, 11, "§7Online: §a§l" + online + "  §7| §f" + date);
        setLine(board, 10, "   ");
        setLine(board, 9,  "§7Rang:   " + rank.trim());
        setLine(board, 8,  "§7Coins:  §6§l" + coins);
        setLine(board, 7,  "§7Bank:   §e" + bank);
        setLine(board, 6,  "§7Claims: §a" + claims + "§7/§a" + maxClaims);
        setLine(board, 5,  "    ");
        setLine(board, 4,  jobNameLine);
        setLine(board, 3,  xpBarLine);
        setLine(board, 2,  bonusLine);
        setLine(board, 1,  friendLine);
        setLine(board, 0,  bottomLine);
    }

    private static String fmtCd(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    private void setLine(Scoreboard board, int score, String text) {
        Team team = board.getTeam("line" + score);
        if (team == null) return;
        team.prefix(Component.text(text));
        team.suffix(Component.empty());
    }

    public void stopAll() {
        if (updateTask != null) updateTask.cancel();
        playerBoards.clear();
    }
}
