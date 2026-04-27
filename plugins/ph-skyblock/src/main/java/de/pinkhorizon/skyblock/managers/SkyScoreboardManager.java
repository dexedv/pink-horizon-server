package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkyScoreboardManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    // Eindeutige unsichtbare Präfixe für jede Zeile (§0..§9)
    private static final String[] SPACERS = {
        "§0§r", "§1§r", "§2§r", "§3§r", "§4§r",
        "§5§r", "§6§r", "§7§r", "§8§r", "§9§r"
    };

    public SkyScoreboardManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        startTicker();
    }

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    update(p);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // alle 2 Sekunden
    }

    public void show(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective(
            "sb", Criteria.DUMMY,
            MM.deserialize("<gold><bold>✦ SkyBlock ✦</bold></gold>")
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

        // Alle alten Einträge entfernen
        for (String entry : sb.getEntries()) sb.resetScores(entry);

        SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        Island island = (sp != null && sp.getIslandId() != null)
            ? plugin.getIslandManager().getIslandById(sp.getIslandId())
            : null;

        int online = Bukkit.getOnlinePlayers().size();

        // Zeilen von unten (niedrigster Score) nach oben (höchster Score)
        int i = 0;
        set(obj, "§dpinkhorizon.fun", i++);
        set(obj, SPACERS[0], i++);
        set(obj, "§7Online: §a" + online + " §7Spieler", i++);
        set(obj, SPACERS[1], i++);
        set(obj, "§e§lInsel-Info", i++);
        set(obj, "§7Level: §6" + (island != null ? island.getLevel() : "§8–"), i++);
        set(obj, "§7Score: §f" + (island != null ? island.getScore() : "§80"), i++);
        set(obj, "§7Größe: §f" + (island != null ? island.getSize() + "×" + island.getSize() : "§8–"), i++);
        set(obj, SPACERS[2], i++);
    }

    private void set(Objective obj, String text, int score) {
        obj.getScore(text).setScore(score);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}
