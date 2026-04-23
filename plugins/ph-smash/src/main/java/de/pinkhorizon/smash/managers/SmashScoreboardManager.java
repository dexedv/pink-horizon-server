package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.boss.ActiveBoss;
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

    private final PHSmash             plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask                task;

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
        ActiveBoss boss = plugin.getBossManager().getActiveBoss();

        int    bossLevel  = boss != null ? boss.getConfig().level() : plugin.getPlayerDataManager().getGlobalBossLevel();
        String bossHpLine;
        if (boss != null) {
            double pct    = boss.getHpPercent() * 100;
            int    filled = (int) (pct / 10);
            String bar    = "§c" + "█".repeat(filled) + "§8" + "░".repeat(10 - filled);
            bossHpLine = "§8[" + bar + "§8] §f" + String.format("%.1f%%", pct);
        } else {
            bossHpLine = "§8Kein Boss aktiv";
        }

        long totalDmg = plugin.getPlayerDataManager().getTotalDamage(uuid);
        int  kills    = plugin.getPlayerDataManager().getKills(uuid);

        int atkLevel  = plugin.getUpgradeManager().getLevel(uuid, UpgradeManager.UpgradeType.ATTACK);
        int hpLevel   = plugin.getUpgradeManager().getLevel(uuid, UpgradeManager.UpgradeType.HEALTH);

        setLine(board, 13, " ");
        setLine(board, 12, "§7Boss-Level:  §c§l" + bossLevel);
        setLine(board, 11, "§7Boss-HP:");
        setLine(board, 10, bossHpLine);
        setLine(board, 9,  "  ");
        setLine(board, 8,  "§7Schaden: §e" + formatDmg(totalDmg));
        setLine(board, 7,  "§7Kills:   §a" + kills);
        setLine(board, 6,  "   ");
        setLine(board, 5,  "§7⚔ Angriff: §6+" + (atkLevel * 10) + "%");
        setLine(board, 4,  "§7❤ HP-Bonus: §a+" + (hpLevel * 4));
        setLine(board, 3,  "    ");
        setLine(board, 2,  "§aplay.pinkhorizon.fun");
        setLine(board, 1,  " ");
        setLine(board, 0,  "  ");
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
