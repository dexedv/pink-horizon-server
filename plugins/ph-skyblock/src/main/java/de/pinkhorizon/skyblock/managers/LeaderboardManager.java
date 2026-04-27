package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet die Top-10-Leaderboards (Coins, Quests, Mined).
 * Daten werden alle 5 Minuten async aus der DB geladen und gecacht.
 */
public class LeaderboardManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;

    private record Entry(String name, long value) {}

    private final List<Entry> topCoins  = new ArrayList<>();
    private final List<Entry> topQuests = new ArrayList<>();
    private final List<Entry> topMined  = new ArrayList<>();

    public LeaderboardManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        startRefreshTask();
    }

    private void startRefreshTask() {
        // Sofort laden, dann alle 5 Minuten
        refresh();
        new BukkitRunnable() {
            @Override public void run() { refresh(); }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L);
    }

    public void refresh() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            loadTop("coins",             topCoins);
            loadTop("total_quests_done", topQuests);
            loadTop("total_mined",       topMined);
        });
    }

    private void loadTop(String column, List<Entry> target) {
        String sql = """
            SELECT p.name, e.%s
            FROM sky_player_ext e
            JOIN sky_players p ON p.uuid = e.uuid
            ORDER BY e.%s DESC
            LIMIT 10
        """.formatted(column, column);

        List<Entry> result = new ArrayList<>();
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Entry(rs.getString("name"), rs.getLong(column)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Leaderboard load fehler (" + column + "): " + e.getMessage());
        }

        synchronized (target) {
            target.clear();
            target.addAll(result);
        }
    }

    // ── Anzeige ───────────────────────────────────────────────────────────────

    public void showTopCoins(Player player) {
        sendHeader(player, "Top 10 – Meiste Coins");
        synchronized (topCoins) {
            for (int i = 0; i < topCoins.size(); i++) {
                Entry e = topCoins.get(i);
                player.sendMessage(MM.deserialize(
                    rank(i + 1) + " <white>" + e.name() + " <gray>– <gold>" + String.format("%,d", e.value()) + " Coins"));
            }
        }
        sendFooter(player);
    }

    public void showTopQuests(Player player) {
        sendHeader(player, "Top 10 – Meiste Quests");
        synchronized (topQuests) {
            for (int i = 0; i < topQuests.size(); i++) {
                Entry e = topQuests.get(i);
                player.sendMessage(MM.deserialize(
                    rank(i + 1) + " <white>" + e.name() + " <gray>– <aqua>" + String.format("%,d", e.value()) + " Quests"));
            }
        }
        sendFooter(player);
    }

    public void showTopMined(Player player) {
        sendHeader(player, "Top 10 – Meiste Blöcke");
        synchronized (topMined) {
            for (int i = 0; i < topMined.size(); i++) {
                Entry e = topMined.get(i);
                player.sendMessage(MM.deserialize(
                    rank(i + 1) + " <white>" + e.name() + " <gray>– <green>" + String.format("%,d", e.value()) + " Blöcke"));
            }
        }
        sendFooter(player);
    }

    private String rank(int pos) {
        return switch (pos) {
            case 1 -> "<gold><bold>#1";
            case 2 -> "<gray><bold>#2";
            case 3 -> "<#CD7F32><bold>#3";
            default -> "<dark_gray>#" + pos;
        };
    }

    private void sendHeader(Player p, String title) {
        p.sendMessage(MM.deserialize("<light_purple>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        p.sendMessage(MM.deserialize("<light_purple><bold>  ✦ " + title + " ✦"));
        p.sendMessage(MM.deserialize("<light_purple>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void sendFooter(Player p) {
        p.sendMessage(MM.deserialize("<light_purple>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }
}
