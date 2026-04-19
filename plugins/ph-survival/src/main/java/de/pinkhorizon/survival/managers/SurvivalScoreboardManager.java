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

public class SurvivalScoreboardManager {

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

        for (int i = 0; i < 12; i++) {
            Team team = board.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            obj.getScore(ENTRIES[i]).setScore(i);
        }

        playerBoards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        refreshLines(player, board);
    }

    public void removeScoreboard(Player player) {
        playerBoards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
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
        String date   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        int online    = Bukkit.getOnlinePlayers().size();
        long coins    = plugin.getEconomyManager().getBalance(player.getUniqueId());
        int claims    = plugin.getClaimManager().getClaimCount(player.getUniqueId());
        int maxClaims = plugin.getRankManager().getMaxClaims(player.getUniqueId());
        String rank   = plugin.getRankManager().getRank(player.getUniqueId()).chatPrefix;

        setLine(board, 11, " ");
        setLine(board, 10, "§a§l» §fSurvival-Server");
        setLine(board, 9,  "  ");
        setLine(board, 8,  "§7Online: §a§l" + online);
        setLine(board, 7,  "§7Datum:  §f" + date);
        setLine(board, 6,  "   ");
        setLine(board, 5,  "§7Rang:   " + rank.trim());
        setLine(board, 4,  "§7Coins:  §6§l" + coins);
        setLine(board, 3,  "§7Claims: §a" + claims + "§7/§a" + maxClaims);
        setLine(board, 2,  "    ");
        setLine(board, 1,  "§aplay.pinkhorizon.de");
        setLine(board, 0,  "     ");
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
