package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class PlaytimeRewardManager {

    private static final long INTERVAL_TICKS = 20L * 60 * 10; // alle 10 Minuten
    private static final long REWARD_AMOUNT  = 50;             // Coins pro Intervall

    private final PHSurvival plugin;

    public PlaytimeRewardManager(PHSurvival plugin) {
        this.plugin = plugin;

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::giveRewards, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    private void giveRewards() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // AFK-Spieler bekommen keine Belohnung
            if (plugin.getAfkManager().isAfk(player.getUniqueId())) continue;

            plugin.getEconomyManager().deposit(player.getUniqueId(), REWARD_AMOUNT);
            plugin.getStatsManager().addPlaytime(player.getUniqueId(), 10);

            player.sendActionBar(Component.text(
                "§a+" + REWARD_AMOUNT + " Coins §7für 10 Minuten Spielzeit!"));
        }
    }
}
