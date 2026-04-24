package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.lobby.PHLobby;
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
import java.util.List;
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

    // 16 unique invisible entries (§0§r .. §f§r)
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

    // Fixed header + footer slots: blank, title, blank, online, date, separator → 6 lines
    // + one line per server + website line at score 0 → total = 7 + servers.size()
    private int lineCount() {
        ServerStatusManager ssm = plugin.getServerStatusManager();
        if (ssm == null) return 10; // fallback
        return 7 + ssm.getServers().size();
    }

    public void giveScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective("ph_sb", Criteria.DUMMY,
                Component.text(TITLE_FRAMES[0]));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int lines = lineCount();
        for (int i = 0; i < lines; i++) {
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

    /** Wird vom ServerStatusManager nach jedem Ping-Zyklus aufgerufen. */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = playerBoards.get(player.getUniqueId());
            if (board == null) continue;
            Objective obj = board.getObjective("ph_sb");
            if (obj == null) continue;
            refreshLines(player, board, obj);
        }
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
        ServerStatusManager ssm = plugin.getServerStatusManager();
        List<ServerStatusManager.ServerEntry> servers = (ssm != null) ? ssm.getServers() : List.of();

        String date   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        int    online = Bukkit.getOnlinePlayers().size();

        // top = highest score (topmost visible line)
        int top = servers.size() + 6;

        setLine(board, top,     " ");
        setLine(board, top - 1, "\u00a7d\u00a7l\u00bb \u00a7fServer-Hub");
        setLine(board, top - 2, "  ");
        setLine(board, top - 3, "\u00a77Online: \u00a7d\u00a7l" + online);
        setLine(board, top - 4, "\u00a77Datum:  \u00a7f" + date);
        setLine(board, top - 5, "   ");

        // Server-Status-Zeilen
        for (int i = 0; i < servers.size(); i++) {
            ServerStatusManager.ServerEntry srv = servers.get(i);
            ServerStatusManager.Status status = (ssm != null) ? ssm.getStatus(srv.id()) : ServerStatusManager.Status.OFFLINE;
            int players = (ssm != null) ? ssm.getPlayerCount(srv.id()) : 0;
            setLine(board, top - 6 - i, buildServerLine(srv.display(), status, players));
        }

        setLine(board, 0, "\u00a7dplay.pinkhorizon.de");
    }

    private String buildServerLine(String display, ServerStatusManager.Status status, int players) {
        return switch (status) {
            case ONLINE     -> "\u00a7a\u25cf \u00a7f" + display + " \u00a78| \u00a7f" + players + " \u00a77Spieler";
            case RESTARTING -> "\u00a7e\u25cf \u00a7f" + display + " \u00a78| \u00a7eNeustart...";
            case OFFLINE    -> "\u00a7c\u25cf \u00a7f" + display + " \u00a78| \u00a7cOffline";
        };
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
