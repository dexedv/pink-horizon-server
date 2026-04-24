package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.core.PHCore;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager {

    private final PHLobby plugin;
    private final Map<UUID, Scoreboard> playerBoards    = new HashMap<>();
    private final Map<UUID, Boolean>    discordVerified = new HashMap<>();
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

    // Header: blank, title, blank, online, date, discord, separator → 7 lines
    // + one line per server + website line at score 0 → total = 8 + servers.size()
    private int lineCount() {
        ServerStatusManager ssm = plugin.getServerStatusManager();
        if (ssm == null) return 11; // fallback
        return 8 + ssm.getServers().size();
    }

    /** Async laden des Discord-Verifizierungsstatus – beim Join aufrufen. */
    public void loadDiscordStatus(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean verified = false;
            try (Connection c = PHCore.getInstance().getDatabaseManager().getConnection();
                 PreparedStatement st = c.prepareStatement(
                     "SELECT 1 FROM discord_sync WHERE uuid = ? AND verified_at IS NOT NULL LIMIT 1")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    verified = rs.next();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("ScoreboardManager.loadDiscordStatus: " + e.getMessage());
            }
            discordVerified.put(uuid, verified);
        });
    }

    /** Beim Quit aufräumen. */
    public void removeDiscordStatus(UUID uuid) {
        discordVerified.remove(uuid);
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

        String date    = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        int    online  = Bukkit.getOnlinePlayers().size();
        boolean dcVerified = discordVerified.getOrDefault(player.getUniqueId(), false);
        String dcLine  = dcVerified
            ? "\u00a75Discord: \u00a7a\u2714 Verifiziert"
            : "\u00a75Discord: \u00a7c\u2718 /sync discord";

        // top = highest score (topmost visible line)
        int top = servers.size() + 7;

        setLine(board, top,     " ");
        setLine(board, top - 1, "\u00a7d\u00a7l\u00bb \u00a7fServer-Hub");
        setLine(board, top - 2, "  ");
        setLine(board, top - 3, "\u00a77Online: \u00a7d\u00a7l" + online);
        setLine(board, top - 4, "\u00a77Datum:  \u00a7f" + date);
        setLine(board, top - 5, dcLine);
        setLine(board, top - 6, "   ");

        // Server-Status-Zeilen
        for (int i = 0; i < servers.size(); i++) {
            ServerStatusManager.ServerEntry srv = servers.get(i);
            ServerStatusManager.Status status = (ssm != null) ? ssm.getStatus(srv.id()) : ServerStatusManager.Status.OFFLINE;
            int players = (ssm != null) ? ssm.getPlayerCount(srv.id()) : 0;
            setLine(board, top - 7 - i, buildServerLine(srv.display(), status, players));
        }

        setLine(board, 0, "\u00a7dplay.pinkhorizon.fun");
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
