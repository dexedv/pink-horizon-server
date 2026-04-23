package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.arena.ArenaInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SmashScoreboardManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String TITLE = "§c§lSmash the Boss";

    private static final String[] ENTRIES;
    static {
        String hex = "0123456789abcdef";
        ENTRIES = new String[16];
        for (int i = 0; i < 16; i++)
            ENTRIES[i] = "\u00a7" + hex.charAt(i) + "\u00a7r";
    }

    private final PHSmash              plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask                 task;

    public SmashScoreboardManager(PHSmash plugin) {
        this.plugin = plugin;
        startTask();
    }

    public void giveScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective  obj   = board.registerNewObjective("smash", Criteria.DUMMY, LEGACY.deserialize(TITLE));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < 14; i++) {
            Team team = board.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            obj.getScore(ENTRIES[i]).setScore(i);
        }

        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        refresh(player, board);
    }

    public void removeScoreboard(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Einzelnen Spieler aktualisieren (nach Schaden, Boss-Tod, etc.) */
    public void update(Player player) {
        Scoreboard b = boards.get(player.getUniqueId());
        if (b != null) refresh(player, b);
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b != null) refresh(p, b);
        }
    }

    private void startTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    private void refresh(Player player, Scoreboard board) {
        UUID uuid = player.getUniqueId();
        ArenaInstance arena = plugin.getArenaManager().getArena(uuid);

        int    bossLevel;
        String bossHpLine;
        String bossHpRaw;

        if (arena != null) {
            bossLevel = arena.getBossLevel();
            double pct    = arena.getHpPercent() * 100;
            int    filled = (int) (pct / 10);
            String bar    = "§c" + "█".repeat(filled) + "§8" + "░".repeat(10 - filled);
            bossHpLine = "§8[" + bar + "§8] §f" + String.format("%.1f%%", pct);
            bossHpRaw  = arena.getConfig().formatHp(arena.getCurrentHp())
                       + " §8/ " + arena.getConfig().formatHp(arena.getConfig().maxHp());
        } else {
            bossLevel  = plugin.getPlayerDataManager().getPersonalBossLevel(uuid);
            bossHpLine = "§7/stb join §8→ §7Beitreten";
            bossHpRaw  = "";
        }

        long totalDmg = plugin.getPlayerDataManager().getTotalDamage(uuid);
        int  kills    = plugin.getPlayerDataManager().getKills(uuid);
        int  atkLevel = plugin.getUpgradeManager().getLevel(uuid, UpgradeManager.UpgradeType.ATTACK);
        int  hpLevel  = plugin.getUpgradeManager().getLevel(uuid, UpgradeManager.UpgradeType.HEALTH);
        long coins    = plugin.getCoinManager().getCoins(uuid);

        setLine(board, 13, " ");
        setLine(board, 12, "§7Dein Boss:  §c§l" + bossLevel);
        setLine(board, 11, "§7Boss-HP:");
        setLine(board, 10, bossHpLine);
        setLine(board, 9,  arena != null ? "§8" + bossHpRaw : "  ");
        String combo = arena != null ? plugin.getComboManager().getComboDisplay(uuid) : "";
        setLine(board, 8,  combo.isEmpty() ? "   " : combo);
        setLine(board, 7,  "§7Schaden: §e" + formatDmg(totalDmg));
        setLine(board, 6,  "§7Kills:   §a" + kills);
        setLine(board, 5,  "§7Münzen:  §6" + coins);
        String streak = plugin.getStreakManager().getStreakDisplay(uuid);
        String rank   = RankManager.getRankDisplay(bossLevel);
        setLine(board, 4,  "§7Streak: " + streak);
        setLine(board, 3,  "§7⚔ Angriff: §6+" + (atkLevel * 8) + "%");
        setLine(board, 2,  "§7❤ HP-Bonus: §a+" + (hpLevel * 6));
        setLine(board, 1,  "§aplay.pinkhorizon.fun");
        setLine(board, 0,  rank);
    }

    private void setLine(Scoreboard board, int score, String text) {
        Team team = board.getTeam("line" + score);
        if (team == null) return;
        team.prefix(LEGACY.deserialize(text));
        team.suffix(Component.empty());
    }

    private static String formatDmg(long dmg) {
        if (dmg >= 1_000_000_000) return String.format("%.2fB", dmg / 1_000_000_000.0);
        if (dmg >= 1_000_000)     return String.format("%.2fM", dmg / 1_000_000.0);
        if (dmg >= 1_000)         return String.format("%.1fK", dmg / 1_000.0);
        return String.valueOf(dmg);
    }

    public void stopAll() {
        if (task != null) task.cancel();
        boards.clear();
    }
}
