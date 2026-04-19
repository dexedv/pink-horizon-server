package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;

public class SurvivalJoinListener implements Listener {

    private final PHSurvival plugin;

    public SurvivalJoinListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Join-Nachricht
        event.joinMessage(
            Component.text("§8[§a+§8] ", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), TextColor.color(0x55FF55)))
        );

        // Spielmodus
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);

        // Scoreboard + Tab
        plugin.getScoreboardManager().giveScoreboard(player);
        plugin.getTabManager().update(player);
        plugin.getRankManager().applyTabName(player);

        // Starter-Coins bei Erstjoin
        if (!player.hasPlayedBefore()) {
            long startBalance = plugin.getConfig().getLong("economy.start-balance", 500);
            plugin.getEconomyManager().deposit(player.getUniqueId(), startBalance);
            player.sendMessage(Component.text(
                "§aDu hast §f" + startBalance + " §aStarter-Coins erhalten!", NamedTextColor.GREEN));
        }

        // Title (1 Tick verzögert)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean firstJoin = !player.hasPlayedBefore();
            Component title    = Component.text("Survival", TextColor.color(0x55FF55), TextDecoration.BOLD);
            Component subtitle = firstJoin
                ? Component.text("Willkommen auf Pink Horizon!", NamedTextColor.GRAY)
                : Component.text("Willkommen zurück, " + player.getName() + "!", NamedTextColor.GRAY);

            player.showTitle(Title.title(title, subtitle,
                Title.Times.times(
                    Duration.ofMillis(500),
                    Duration.ofSeconds(3),
                    Duration.ofMillis(1000)
                )));

            player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);

            // Aktiven Fly wiederherstellen
            plugin.getUpgradeManager().restoreFly(player);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getScoreboardManager().removeScoreboard(player);

        event.quitMessage(
            Component.text("§8[§c-§8] ", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
        );
    }
}
