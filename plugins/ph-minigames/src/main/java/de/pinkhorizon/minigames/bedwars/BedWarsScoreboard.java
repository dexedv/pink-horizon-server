package de.pinkhorizon.minigames.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zeigt eine BedWars-Sidebar pro Spieler mit Teamstatus.
 */
public class BedWarsScoreboard {

    private static final String TITLE = "§d§lBedWars";

    private static final String[] ENTRIES;
    static {
        String hex = "0123456789abcdef";
        ENTRIES = new String[16];
        for (int i = 0; i < 16; i++)
            ENTRIES[i] = "\u00a7" + hex.charAt(i) + "\u00a7r";
    }

    private final Map<BedWarsTeamColor, Scoreboard>   boards  = new EnumMap<>(BedWarsTeamColor.class);
    private final BedWarsGame                         game;

    public BedWarsScoreboard(BedWarsGame game) {
        this.game = game;
    }

    public void give(Player player) {
        BedWarsTeamColor color = game.getTeamOf(player.getUniqueId());
        if (color == null) return;

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective  obj   = board.registerNewObjective("bw", Criteria.DUMMY, TITLE);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int lines = 4 + game.getArena().maxTeams; // Leerzeilen + Kopf + Teams
        for (int i = 0; i <= lines + 2; i++) {
            Team team = board.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            obj.getScore(ENTRIES[i]).setScore(i);
        }

        player.setScoreboard(board);
        update(player, board, obj);
    }

    public void updateAll() {
        for (UUID uuid : game.getAllPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            Scoreboard board = p.getScoreboard();
            if (board == null) continue;
            Objective obj = board.getObjective("bw");
            if (obj == null) continue;
            update(p, board, obj);
        }
    }

    private void update(Player player, Scoreboard board, Objective obj) {
        BedWarsGame.GameState state    = game.getState();
        BedWarsArenaConfig    arena    = game.getArena();

        setLine(board, arena.maxTeams + 3, " ");
        setLine(board, arena.maxTeams + 2, "§fMap: §d" + arena.name);
        setLine(board, arena.maxTeams + 1, "  ");

        // Team-Status-Zeilen
        BedWarsTeamColor[] allColors = BedWarsTeamColor.values();
        int row = arena.maxTeams;
        for (int i = 0; i < arena.maxTeams; i++) {
            BedWarsTeamColor tc = allColors[i];
            String bedSymbol;
            if (!game.isTeamAlive(tc)) {
                bedSymbol = "§7✗";
            } else if (game.isBedAlive(tc)) {
                bedSymbol = "§a✔";
            } else {
                bedSymbol = "§e♥ " + game.getTeamSize(tc);
            }
            setLine(board, row--, tc.chatColor + tc.displayName.replace(tc.chatColor, "") + " " + bedSymbol);
        }

        setLine(board, row--, "   ");

        if (state == BedWarsGame.GameState.STARTING) {
            setLine(board, row, "§eStart in §f" + game.getCountdown() + "s");
        } else {
            setLine(board, row, "§dpinkhorizon.de");
        }
    }

    private void setLine(Scoreboard board, int score, String text) {
        Team team = board.getTeam("line" + score);
        if (team == null) return;
        team.prefix(net.kyori.adventure.text.Component.text(text));
        team.suffix(net.kyori.adventure.text.Component.empty());
    }

    public void remove(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
