package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.lobby.PHLobby;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ServerBossBarManager {

    private static final String[] DOTS = {"", ".", "..", "..."};

    private final PHLobby plugin;

    /** Eine BossBar pro Server-ID. */
    private final Map<String, BossBar>                      bars           = new LinkedHashMap<>();
    /** Snapshot des letzten bekannten Status (für Animation). */
    private final Map<String, ServerStatusManager.Status>   lastStatus     = new HashMap<>();
    private final Map<String, Integer>                      lastPlayers    = new HashMap<>();
    private final Map<String, String>                       displayNames   = new HashMap<>();

    private int        animTick = 0;
    private BukkitTask animTask;

    public ServerBossBarManager(PHLobby plugin) {
        this.plugin = plugin;
    }

    /** Wird nach dem Laden der Server-Liste aufgerufen. */
    public void init(List<ServerStatusManager.ServerEntry> servers) {
        for (ServerStatusManager.ServerEntry s : servers) {
            displayNames.put(s.id(), s.display());
            lastStatus.put(s.id(), ServerStatusManager.Status.OFFLINE);
            lastPlayers.put(s.id(), 0);

            BossBar bar = Bukkit.createBossBar(
                    buildTitle(s.display(), ServerStatusManager.Status.OFFLINE, 0, 0),
                    BarColor.RED, BarStyle.SOLID);
            bar.setProgress(0.3);
            bar.setVisible(true);
            bars.put(s.id(), bar);
        }

        // Animations-Timer: einmal pro Sekunde
        animTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::animateTick, 20L, 20L);
    }

    /** Vom ServerStatusManager nach jedem Ping-Zyklus aufgerufen (Main-Thread). */
    public void updateAll(ServerStatusManager sm) {
        for (ServerStatusManager.ServerEntry s : sm.getServers()) {
            BossBar bar = bars.get(s.id());
            if (bar == null) continue;

            ServerStatusManager.Status status  = sm.getStatus(s.id());
            int                        players = sm.getPlayerCount(s.id());

            lastStatus.put(s.id(), status);
            lastPlayers.put(s.id(), players);

            applyToBar(bar, s.display(), status, players);
        }
    }

    private void animateTick() {
        animTick = (animTick + 1) % 4;
        for (Map.Entry<String, BossBar> e : bars.entrySet()) {
            String  id     = e.getKey();
            BossBar bar    = e.getValue();
            ServerStatusManager.Status status = lastStatus.getOrDefault(id, ServerStatusManager.Status.OFFLINE);

            if (status == ServerStatusManager.Status.RESTARTING) {
                // Pulsierender Fortschrittsbalken + animierte Punkte
                double phase = 0.5 + 0.5 * Math.sin(animTick * Math.PI / 2.0);
                bar.setProgress(Math.max(0.05, Math.min(1.0, phase)));
                String display = displayNames.getOrDefault(id, id);
                bar.setTitle("§e⬤ §f" + display + " §8│ §eNeustart" + DOTS[animTick]);
            }
        }
    }

    private void applyToBar(BossBar bar, String display, ServerStatusManager.Status status, int players) {
        switch (status) {
            case ONLINE -> {
                bar.setColor(BarColor.GREEN);
                bar.setProgress(1.0);
                bar.setTitle(buildTitle(display, status, players, animTick));
            }
            case RESTARTING -> {
                bar.setColor(BarColor.YELLOW);
                bar.setProgress(0.75);
                bar.setTitle(buildTitle(display, status, players, animTick));
            }
            case OFFLINE -> {
                bar.setColor(BarColor.RED);
                bar.setProgress(0.3);
                bar.setTitle(buildTitle(display, status, players, animTick));
            }
        }
    }

    private String buildTitle(String display, ServerStatusManager.Status status, int players, int tick) {
        return switch (status) {
            case ONLINE     -> "§a⬤ §f" + display + " §8│ §aOnline §8│ §f" + players + " §7Spieler";
            case RESTARTING -> "§e⬤ §f" + display + " §8│ §eNeustart" + DOTS[tick % 4];
            case OFFLINE    -> "§c⬤ §f" + display + " §8│ §cOffline";
        };
    }

    public void showAll(Player player) {
        bars.values().forEach(b -> b.addPlayer(player));
    }

    public void hideAll(Player player) {
        bars.values().forEach(b -> b.removePlayer(player));
    }

    public void stop() {
        if (animTask != null) animTask.cancel();
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
    }
}
