package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Verwaltet das Leaderboard (Top-10 Spieler nach Geld).
 * Cached die Liste und aktualisiert sie asynchron alle 60 Sekunden.
 */
public class LeaderboardManager {

    private final PHGenerators plugin;

    private List<PlayerData> cachedTop = new ArrayList<>();
    private BukkitTask updateTask;

    public LeaderboardManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void start() {
        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::update, 200L, 1200L);
    }

    public void stop() {
        if (updateTask != null) updateTask.cancel();
    }

    private void update() {
        // Online-Spieler aus in-memory + DB für Offline-Spieler
        List<PlayerData> all = new ArrayList<>(plugin.getPlayerDataMap().values());
        // Füge DB-Top-Einträge hinzu falls nicht bereits online
        List<PlayerData> dbTop = plugin.getRepository().getTopPlayers(20);
        for (PlayerData db : dbTop) {
            boolean alreadyOnline = all.stream().anyMatch(p -> p.getUuid().equals(db.getUuid()));
            if (!alreadyOnline) all.add(db);
        }

        all.sort(Comparator.comparingLong(PlayerData::getMoney).reversed());
        cachedTop = all.stream().limit(10).toList();
    }

    /** Gibt das formatierte Leaderboard zurück */
    public String getLeaderboardText() {
        if (cachedTop.isEmpty()) {
            update();
        }
        StringBuilder sb = new StringBuilder("<gold>━━ Top Generatoren-Spieler ━━\n");
        for (int i = 0; i < cachedTop.size(); i++) {
            PlayerData data = cachedTop.get(i);
            String medal = switch (i) {
                case 0 -> "<gold>🥇";
                case 1 -> "<gray>🥈";
                case 2 -> "<dark_red>🥉";
                default -> "<gray>#" + (i + 1);
            };
            boolean online = Bukkit.getPlayer(data.getUuid()) != null;
            String status = online ? "<green>●" : "<red>●";
            String prestige = data.getPrestige() > 0
                    ? " <dark_purple>[P" + data.getPrestige() + "]</dark_purple>" : "";
            sb.append(medal).append(" ").append(status).append(" <white>")
                    .append(data.getName()).append(prestige)
                    .append(" <gold>$").append(MoneyManager.formatMoney(data.getMoney()))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    public List<PlayerData> getCachedTop() {
        return cachedTop;
    }
}
