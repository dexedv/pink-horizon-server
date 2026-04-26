package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * Verwaltet Saison-Leaderboard-Snapshots.
 * Admin-Command: /gen season snapshot
 */
public class SeasonalLeaderboardManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private int currentSeasonNo = 0;

    public SeasonalLeaderboardManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void init() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            currentSeasonNo = plugin.getRepository().getMaxSeasonNo();
        });
    }

    /** Nimmt einen Snapshot der aktuellen Top-10 und speichert ihn als neue Season */
    public void takeSnapshot(String adminName) {
        int newSeason = currentSeasonNo + 1;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerData> top = plugin.getRepository().getTopPlayers(10);
            for (int i = 0; i < top.size(); i++) {
                PlayerData d = top.get(i);
                plugin.getRepository().saveSeasonEntry(newSeason, i + 1,
                        d.getName(), d.getMoney(), d.getPrestige());
            }
            currentSeasonNo = newSeason;
            plugin.getRepository().setMeta("current_season", String.valueOf(newSeason));

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcast(MM.deserialize(
                        "<gold>✦ Season " + newSeason + " wurde von <white>" + adminName
                        + " <gold>gestartet! Top-10 gespeichert."));
            });
        });
    }

    public String getSeasonLeaderboardText(int seasonNo) {
        if (seasonNo <= 0) seasonNo = currentSeasonNo;
        if (seasonNo <= 0) return "<gray>Noch keine Season gespielt.";

        final int sn = seasonNo;
        List<String[]> entries = plugin.getRepository().getSeasonLeaderboard(sn);
        if (entries.isEmpty()) return "<gray>Keine Daten für Season " + sn + ".";

        String[] MEDALS = {"①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"};
        StringBuilder sb = new StringBuilder("<gold>━━ Season " + sn + " Leaderboard ━━\n");
        for (String[] e : entries) {
            int rank = Integer.parseInt(e[0]);
            String medal = rank <= 10 ? MEDALS[rank - 1] : "#" + rank;
            String color = rank == 1 ? "<gold>" : rank == 2 ? "<gray>" : rank == 3 ? "<dark_red>" : "<white>";
            sb.append(color).append(medal).append(" ").append(e[1])
              .append(" <gray>$").append(MoneyManager.formatMoney(Long.parseLong(e[2])))
              .append(" <light_purple>[P").append(e[3]).append("]\n");
        }
        return sb.toString().trim();
    }

    public int getCurrentSeasonNo() { return currentSeasonNo; }
}
