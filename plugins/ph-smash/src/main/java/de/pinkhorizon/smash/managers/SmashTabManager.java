package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.arena.ArenaInstance;
import de.pinkhorizon.smash.listeners.SmashChatListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class SmashTabManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final String[] HEADER_FRAMES = {
        "\n§c§l⚔ Pink Horizon §7| §4Smash the Boss §c§l⚔\n",
        "\n§4§l⚔ Pink Horizon §7| §cSmash the Boss §4§l⚔\n",
        "\n§c§l☠ Pink Horizon §7| §4Smash the Boss §c§l☠\n",
    };

    private final PHSmash   plugin;
    private BukkitTask      task;
    private int             frame = 0;

    public SmashTabManager(PHSmash plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            frame = (frame + 1) % HEADER_FRAMES.length;
            int online = Bukkit.getOnlinePlayers().size();
            Component header = Component.text(HEADER_FRAMES[frame]);

            for (Player p : Bukkit.getOnlinePlayers()) {
                ArenaInstance arena = plugin.getArenaManager().getArena(p.getUniqueId());
                int bossLevel = arena != null
                    ? arena.getBossLevel()
                    : plugin.getPlayerDataManager().getPersonalBossLevel(p.getUniqueId());

                String lpPrefix = SmashChatListener.getLpPrefix(p);
                String prestige = plugin.getPrestigeManager().getPrestigeDisplay(p.getUniqueId());
                String rank     = RankManager.getRankPrefix(bossLevel);
                String streak   = plugin.getStreakManager().getStreakDisplay(p.getUniqueId());
                String status   = arena != null ? "§aIn Arena" : "§7Lobby";

                // Tab-Spielername: [LP-Rang] [Prestige] Name
                p.playerListName(buildTabName(lpPrefix, prestige, p.getName()));

                Component footer = Component.text(
                    "\n" + rank + " §7Rang  §8|  §7Level: §c" + bossLevel
                    + "  §8|  " + streak
                    + "  §8|  §7Online: §a" + online
                    + "  §8|  " + status
                    + "\n§aplay.pinkhorizon.fun\n");

                p.sendPlayerListHeaderAndFooter(header, footer);
            }
        }, 0L, 20L);
    }

    public void update(Player player) {
        ArenaInstance arena = plugin.getArenaManager().getArena(player.getUniqueId());
        int bossLevel = arena != null
            ? arena.getBossLevel()
            : plugin.getPlayerDataManager().getPersonalBossLevel(player.getUniqueId());

        String lpPrefix = SmashChatListener.getLpPrefix(player);
        String prestige = plugin.getPrestigeManager().getPrestigeDisplay(player.getUniqueId());
        String rank     = RankManager.getRankPrefix(bossLevel);
        String streak   = plugin.getStreakManager().getStreakDisplay(player.getUniqueId());
        String status   = arena != null ? "§aIn Arena" : "§7Lobby";

        player.playerListName(buildTabName(lpPrefix, prestige, player.getName()));

        player.sendPlayerListHeaderAndFooter(
            Component.text(HEADER_FRAMES[frame]),
            Component.text("\n" + rank + " §7Rang  §8|  §7Level: §c" + bossLevel
                + "  §8|  " + streak
                + "  §8|  §7Online: §a" + Bukkit.getOnlinePlayers().size()
                + "  §8|  " + status
                + "\n§aplay.pinkhorizon.fun\n"));
    }

    private Component buildTabName(String lpPrefix, String prestige, String name) {
        StringBuilder sb = new StringBuilder();
        if (!lpPrefix.isEmpty()) sb.append(lpPrefix).append(" ");
        if (!prestige.isEmpty()) sb.append(prestige).append(" ");
        sb.append("§f").append(name);
        return LEGACY.deserialize(sb.toString());
    }

    public void stop() {
        if (task != null) task.cancel();
    }
}
