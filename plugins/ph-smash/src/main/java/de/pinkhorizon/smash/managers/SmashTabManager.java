package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class SmashTabManager {

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
            int    bossLevel = plugin.getPlayerDataManager().getGlobalBossLevel();
            int    online    = Bukkit.getOnlinePlayers().size();

            Component header = Component.text(HEADER_FRAMES[frame]);
            Component footer = Component.text(
                "\n§7Boss-Level: §c" + bossLevel
                + "  §7§l|§r  §7Online: §a" + online
                + "\n§aplay.pinkhorizon.fun\n");

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendPlayerListHeaderAndFooter(header, footer);
            }
        }, 0L, 20L);
    }

    public void update(Player player) {
        int bossLevel = plugin.getPlayerDataManager().getGlobalBossLevel();
        player.sendPlayerListHeaderAndFooter(
            Component.text(HEADER_FRAMES[frame]),
            Component.text("\n§7Boss-Level: §c" + bossLevel
                + "  §7§l|§r  §7Online: §a" + Bukkit.getOnlinePlayers().size()
                + "\n§aplay.pinkhorizon.fun\n"));
    }

    public void stop() {
        if (task != null) task.cancel();
    }
}
