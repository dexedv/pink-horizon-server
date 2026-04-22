package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class SurvivalTabManager {

    private final PHSurvival plugin;
    private BukkitTask task;

    private static final String[] HEADER_FRAMES = {
        "\n§a§l✦ Pink Horizon §7| §2Survival §a§l✦\n",
        "\n§2§l✦ Pink Horizon §7| §aSurvival §2§l✦\n",
        "\n§a§l⚔ Pink Horizon §7| §2Survival §a§l⚔\n",
    };
    private int frame = 0;

    public SurvivalTabManager(PHSurvival plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            frame = (frame + 1) % HEADER_FRAMES.length;
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            Component header = Component.text(HEADER_FRAMES[frame]);
            Component footer = Component.text(
                "\n§7Online: §a" + Bukkit.getOnlinePlayers().size()
                + "  §7§l|§r  §7Zeit: §f" + time
                + "\n§7play.pinkhorizon.fun\n"
            );

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendPlayerListHeaderAndFooter(header, footer);
            }
        }, 0L, 20L);
    }

    public void update(Player player) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        player.sendPlayerListHeaderAndFooter(
            Component.text(HEADER_FRAMES[frame]),
            Component.text("\n§7Online: §a" + Bukkit.getOnlinePlayers().size()
                + "  §7§l|§r  §7Zeit: §f" + time
                + "\n§7play.pinkhorizon.fun\n")
        );
    }

    public void stop() {
        if (task != null) task.cancel();
    }
}
