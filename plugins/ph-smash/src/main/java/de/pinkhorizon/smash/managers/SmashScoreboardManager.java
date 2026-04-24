package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.arena.ArenaInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
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

        for (int i = 0; i < 15; i++) {
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

        long totalDmg  = plugin.getPlayerDataManager().getTotalDamage(uuid);
        int  kills     = plugin.getPlayerDataManager().getKills(uuid);
        long coins     = plugin.getCoinManager().getCoins(uuid);

        // Materialien-Vorrat
        int ironQty    = plugin.getLootManager().getQuantity(uuid, LootManager.LootItem.IRON_FRAGMENT);
        int goldQty    = plugin.getLootManager().getQuantity(uuid, LootManager.LootItem.GOLD_FRAGMENT);
        int crystalQty = plugin.getLootManager().getQuantity(uuid, LootManager.LootItem.DIAMOND_SHARD);
        int coreQty    = plugin.getLootManager().getQuantity(uuid, LootManager.LootItem.BOSS_CORE);

        int    prestige = plugin.getPrestigeManager().getPrestige(uuid);
        String streak   = plugin.getStreakManager().getStreakDisplay(uuid);
        String rank     = RankManager.getRankDisplay(bossLevel);

        // Kristall-Hinweis wenn Boss bereit, sonst Combo
        String midLine;
        if (arena != null && arena.isBossReadyToSpawn()) {
            midLine = "§e▶ §6Kristall rechtsklicken!";
        } else {
            String combo = arena != null ? plugin.getComboManager().getComboDisplay(uuid) : "";
            midLine = combo.isEmpty() ? "   " : combo;
        }

        // Prestige + Streak kombiniert
        String streakLine = prestige > 0
            ? "§d✦ §f" + prestige + " Prestige  " + streak
            : "§7Streak: " + streak;

        // Spieler-HP-Leiste
        String hpLine;
        var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (arena != null && hpAttr != null) {
            double maxHp  = hpAttr.getValue();
            double curHp  = Math.max(0, player.getHealth());
            int    filled = (int) Math.round((curHp / maxHp) * 10);
            filled = Math.max(0, Math.min(10, filled));
            String bar    = "§a" + "█".repeat(filled) + "§8" + "░".repeat(10 - filled);
            int curHearts = (int) Math.round(curHp / 2);
            int maxHearts = (int) Math.round(maxHp / 2);
            hpLine = "§c❤ §8[" + bar + "§8] §f" + curHearts + "§8/§f" + maxHearts + "§7♥";
        } else {
            hpLine = " ";
        }

        setLine(board, 14, hpLine);
        setLine(board, 13, "§7Dein Boss:  §c§l" + bossLevel);
        setLine(board, 12, "§7Boss-HP:");
        setLine(board, 11, bossHpLine);
        setLine(board, 10, arena != null ? "§8" + bossHpRaw : "  ");
        setLine(board, 9,  midLine);
        setLine(board, 8,  "§7Schaden: §e" + formatDmg(totalDmg));
        setLine(board, 7,  "§7Kills:   §a" + kills);
        setLine(board, 6,  "§7Münzen:  §6" + coins);
        setLine(board, 5,  "§7◆ §f" + ironQty + "  §6◆ §f" + goldQty + "  §b◆ §f" + crystalQty + "  §5◆ §f" + coreQty);
        setLine(board, 4,  "§8Eisen  Gold  Kristall  Kern");
        setLine(board, 3,  streakLine);
        setLine(board, 2,  rank);
        long afkSec     = plugin.getAfkManager().getAfkSeconds(uuid);
        boolean farming = plugin.getAfkManager().isFarming(uuid);
        String afkLine  = farming
            ? "§b⏰ §7Farm: §b" + de.pinkhorizon.smash.managers.AfkManager.formatTime(afkSec) + " §8(aktiv)"
            : afkSec > 0 ? "§b⏰ §7AFK: §b" + de.pinkhorizon.smash.managers.AfkManager.formatTime(afkSec) : "§8⏰ §7AFK: §8–";
        setLine(board, 1,  afkLine);
        setLine(board, 0,  "§aplay.pinkhorizon.fun");
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
