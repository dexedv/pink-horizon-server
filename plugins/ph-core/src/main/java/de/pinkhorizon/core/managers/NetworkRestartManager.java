package de.pinkhorizon.core.managers;

import de.pinkhorizon.core.PHCore;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Plant täglich um 00:00 Uhr einen automatischen Netzwerk-Neustart.
 * 5 Minuten vorher wird auf allen Spielern eine Bossbar mit Countdown angezeigt.
 * Chat-Ankündigungen bei 5m / 3m / 2m / 1m / 30s / 10s.
 *
 * Neue Spieler, die während des Countdowns joinen, werden automatisch
 * zur Bossbar hinzugefügt (via addPlayer(player) aus dem JoinQuitListener).
 */
public class NetworkRestartManager {

    private static final int WARN_SECONDS = 5 * 60;   // 300 s Vorlauf

    private final PHCore plugin;
    private BossBar   bossBar;
    private BukkitTask scheduleTask;
    private BukkitTask countdownTask;

    public NetworkRestartManager(PHCore plugin) {
        this.plugin = plugin;
        scheduleNext();
    }

    // ── Planung ───────────────────────────────────────────────────────────

    private void scheduleNext() {
        LocalDateTime now      = LocalDateTime.now(ZoneId.systemDefault());
        // nächste Mitternacht
        LocalDateTime midnight = now.toLocalDate().atTime(0, 0, 0).plusDays(1);
        // Warnzeitpunkt = Mitternacht – 5 min
        LocalDateTime warnAt   = midnight.minusSeconds(WARN_SECONDS);

        long delayMs    = ChronoUnit.MILLIS.between(now, warnAt);
        long delayTicks = Math.max(1L, delayMs / 50);

        plugin.getLogger().info("[NetworkRestart] Nächster Neustart: " + midnight
            + " – Ankündigung in " + (delayMs / 1000 / 60) + " Minuten.");

        scheduleTask = Bukkit.getScheduler().runTaskLater(plugin,
            () -> startCountdown(WARN_SECONDS), delayTicks);
    }

    // ── Countdown ─────────────────────────────────────────────────────────

    private void startCountdown(int totalSeconds) {
        bossBar = Bukkit.createBossBar(formatTitle(totalSeconds), BarColor.YELLOW, BarStyle.SOLID);
        bossBar.setProgress(1.0);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);

        broadcastChat(totalSeconds);   // erste Ankündigung sofort

        final int[] remaining = {totalSeconds};

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            remaining[0]--;

            if (remaining[0] <= 0) {
                countdownTask.cancel();
                bossBar.removeAll();
                bossBar = null;
                Bukkit.broadcastMessage("§c§l[!] §cServer wird jetzt neu gestartet – bis gleich!");
                Bukkit.getScheduler().runTaskLater(plugin, Bukkit.getServer()::shutdown, 40L);
                return;
            }

            // Bossbar aktualisieren
            double progress = (double) remaining[0] / totalSeconds;
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
            bossBar.setTitle(formatTitle(remaining[0]));
            bossBar.setColor(remaining[0] <= 60 ? BarColor.RED : BarColor.YELLOW);

            // Chat-Ankündigungen
            broadcastChat(remaining[0]);

        }, 20L, 20L);
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private static String formatTitle(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return "§c§l⚠ Netzwerk-Neustart §8in §e" + String.format("%d:%02d", m, s);
    }

    private static void broadcastChat(int remaining) {
        if (remaining != 300 && remaining != 180 && remaining != 120
                && remaining != 60 && remaining != 30 && remaining != 10
                && remaining != 5 && remaining != 3) return;

        String time = remaining >= 60
            ? (remaining / 60) + " Minute" + (remaining / 60 > 1 ? "n" : "")
            : remaining + " Sekunden";

        Bukkit.broadcastMessage(
            "§c§l[!] §7Das Netzwerk wird in §c" + time + " §7neu gestartet!");
    }

    /**
     * Manuell ausgelöster Neustart (z.B. aus dem Dashboard via RCON-Befehl).
     * Bricht einen laufenden Countdown ab und startet einen neuen.
     */
    public void triggerManual(int seconds) {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
        Bukkit.getScheduler().runTask(plugin, () -> startCountdown(Math.max(10, seconds)));
    }

    /** Joinen während laufendem Countdown → sofort zur Bossbar hinzufügen */
    public void addPlayer(Player player) {
        if (bossBar != null) bossBar.addPlayer(player);
    }

    public void stop() {
        if (scheduleTask  != null) scheduleTask.cancel();
        if (countdownTask != null) countdownTask.cancel();
        if (bossBar       != null) { bossBar.removeAll(); bossBar = null; }
    }
}
