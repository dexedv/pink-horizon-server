package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.enums.TitleType;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkyScoreboardManager {

    private final PHSkyBlock plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    private static final String[] PAD = {
        "§0§r","§1§r","§2§r","§3§r","§4§r",
        "§5§r","§6§r","§7§r","§8§r","§9§r",
        "§a§r","§b§r","§c§r","§d§r","§e§r"
    };

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public SkyScoreboardManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        startTicker();
    }

    private void startTicker() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) update(p);
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    public void show(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective(
            "sb", Criteria.DUMMY,
            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<gradient:#ff69b4:#da70d6><bold>✦ Pink Horizon ✦</bold></gradient>")
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        boards.put(player.getUniqueId(), sb);
        player.setScoreboard(sb);
        update(player);
    }

    public void update(Player player) {
        Scoreboard sb = boards.get(player.getUniqueId());
        if (sb == null) return;
        Objective obj = sb.getObjective("sb");
        if (obj == null) return;

        for (String entry : sb.getEntries()) sb.resetScores(entry);

        UUID uuid   = player.getUniqueId();
        TitleType title = plugin.getTitleManager().getActiveTitle(uuid);
        long coins  = plugin.getCoinManager().getCoins(uuid);
        int online  = Bukkit.getOnlinePlayers().size();
        String date = LocalDate.now().format(DATE_FMT);

        // Insel-Daten von BentoBox
        boolean hasIsland = BentoBoxHook.hasIsland(uuid);
        long level        = BentoBoxHook.getIslandLevel(uuid);
        int size          = BentoBoxHook.getIslandSize(uuid);

        int i = 0;
        line(obj, "§dpinkhorizon.fun",                         i++);
        line(obj, PAD[0],                                      i++);
        line(obj, "§7Datum: §f" + date,                        i++);
        line(obj, "§7Online: §a" + online + " §7Spieler",      i++);
        line(obj, PAD[1],                                      i++);

        line(obj, "§e§lInsel-Info",                            i++);
        if (hasIsland) {
            line(obj, "§7Größe:  §f" + size + "×" + size,     i++);
            line(obj, "§7Level:  §6" + level,                  i++);
        } else {
            line(obj, "§7Keine Insel – §e/is create",          i++);
        }
        line(obj, PAD[2],                                      i++);

        line(obj, "§b§lSpieler",                               i++);
        line(obj, "§7Coins: §6" + formatNum(coins),            i++);
        String titleStr = (title == null || title == TitleType.KEIN_TITEL)
            ? "§8Kein Titel" : strip(title.getChatPrefix()).trim();
        line(obj, "§7Titel:  §f" + titleStr,                   i++);
        line(obj, PAD[3],                                      i++);

        line(obj, "§d§lSkyBlock",                              i++);
    }

    private void line(Objective obj, String text, int score) {
        obj.getScore(text).setScore(score);
    }

    private String formatNum(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }

    private String strip(String s) {
        return s == null ? "" : s.replaceAll("§[0-9a-fk-or]", "");
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}
