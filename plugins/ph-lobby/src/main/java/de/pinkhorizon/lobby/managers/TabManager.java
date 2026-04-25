package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.lobby.PHLobby;
import de.pinkhorizon.lobby.managers.ServerStatusManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TabManager {

    private final PHLobby plugin;
    private BukkitTask task;

    private static final String[] HEADER_FRAMES = {
        "\n\u00a7d\u00a7l\u2605 Pink Horizon \u2605\u00a7r\n",
        "\n\u00a75\u00a7l\u2605 Pink Horizon \u2605\u00a7r\n",
        "\n\u00a7d\u00a7l\u2764 Pink Horizon \u2764\u00a7r\n",
    };
    private int frame = 0;

    public TabManager(PHLobby plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            frame = (frame + 1) % HEADER_FRAMES.length;
            Component header = Component.text(HEADER_FRAMES[frame]);
            Component footer = buildFooter();
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendPlayerListHeaderAndFooter(header, footer);
            }
        }, 20L, 20L);
    }

    public void update(Player player) {
        player.sendPlayerListHeaderAndFooter(
            Component.text(HEADER_FRAMES[frame]),
            buildFooter()
        );
    }

    private Component buildFooter() {
        String time    = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        int    lobby   = Bukkit.getOnlinePlayers().size();
        int    network = lobby;
        ServerStatusManager ssm = plugin.getServerStatusManager();
        if (ssm != null) {
            network += ssm.getServers().stream().mapToInt(s -> ssm.getPlayerCount(s.id())).sum();
        }
        return Component.text(
            "\n\u00a77Lobby: \u00a7d" + lobby
            + "  \u00a77\u00a7l|\u00a7r  \u00a77Netzwerk: \u00a7d" + network
            + "  \u00a77\u00a7l|\u00a7r  \u00a77Zeit: \u00a7f" + time
            + "\n\u00a77play.pinkhorizon.fun\n"
        );
    }

    public void stop() {
        if (task != null) task.cancel();
    }
}
