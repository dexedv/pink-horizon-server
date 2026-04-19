package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

public class ScoreboardManager {

    private final PHLobby plugin;
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private BukkitTask updateTask;

    private static final String[] TITLE_FRAMES = {
        "\u00a7d\u00a7lPink \u00a75\u00a7lHorizon",
        "\u00a75\u00a7lPink \u00a7d\u00a7lHorizon",
        "\u00a7d\u00a7l\u2764 \u00a75\u00a7lPink Horizon \u00a7d\u00a7l\u2764"
    };
    private int titleFrame = 0;

    // 16 einzigartige unsichtbare Eintraege (§0§r .. §f§r)
    private static final String[] ENTRIES;
    static {
        String hex = "0123456789abcdef";
        ENTRIES = new String[16];
        for (int i = 0; i < 16; i++) {
            ENTRIES[i] = "\u00a7" + hex.charAt(i) + "\u00a7r";
        }
    }

    public ScoreboardManager(PHLobby plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    public void giveScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective("ph_sb", Criteria.DUMMY,
                Component.text(TITLE_FRAMES[0]));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Teams + Eintraege einmalig registrieren
        for (int i = 0; i < 11; i++) {
            Team team = board.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            obj.getScore(ENTRIES[i]).setScore(i);
        }

        playerBoards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        refreshLines(player, board, obj);
    }

    public void removeScoreboard(Player player) {
        playerBoards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            titleFrame = (titleFrame + 1) % TITLE_FRAMES.length;
            for (Player player : Bukkit.getOnlinePlayers()) {
                Scoreboard board = playerBoards.get(player.getUniqueId());
                if (board == null) continue;
                Objective obj = board.getObjective("ph_sb");
                if (obj == null) continue;
                obj.displayName(Component.text(TITLE_FRAMES[titleFrame]));
                refreshLines(player, board, obj);
            }
        }, 20L, 20L);
    }

    private void refreshLines(Player player, Scoreboard board, Objective obj) {
        String date  = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        int online   = Bukkit.getOnlinePlayers().size();

        // Score 10 = oberste Zeile, Score 0 = unterste
        setLine(board, 10, " ");
        setLine(board, 9,  "\u00a7d\u00a7l\u00bb \u00a7fServer-Hub");
        setLine(board, 8,  "  ");
        setLine(board, 7,  "\u00a77Online: \u00a7d\u00a7l" + online);
        setLine(board, 6,  "\u00a77Datum:  \u00a7f" + date);
        setLine(board, 5,  "   ");
        setLine(board, 4,  "\u00a77\u00bb \u00a7aSurvival");
        setLine(board, 3,  "\u00a77\u00bb \u00a76SkyBlock");
        setLine(board, 2,  "\u00a77\u00bb \u00a7bMinigames");
        setLine(board, 1,  "    ");
        setLine(board, 0,  "\u00a7dplay.pinkhorizon.de");
    }

    // Nur den Team-Prefix updaten – keine Teams neu registrieren
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
