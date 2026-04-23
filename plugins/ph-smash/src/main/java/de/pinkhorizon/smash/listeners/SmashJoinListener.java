package de.pinkhorizon.smash.listeners;

import de.pinkhorizon.smash.PHSmash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;

public class SmashJoinListener implements Listener {

    private final PHSmash plugin;

    public SmashJoinListener(PHSmash plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        event.joinMessage(
            Component.text("§8[§c+§8] ")
                .append(Component.text(player.getName(), TextColor.color(0xFF5555))));

        // In DB registrieren + Stats anwenden
        plugin.getPlayerDataManager().ensurePlayer(player.getUniqueId());
        plugin.getUpgradeManager().applyStats(player);

        // Scoreboard + Tab
        plugin.getScoreboardManager().giveScoreboard(player);
        plugin.getTabManager().update(player);

        // Begrüßungs-Title mit Boss-Level + Join-Anleitung (1 Tick verzögert)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            int personalLevel = plugin.getPlayerDataManager().getPersonalBossLevel(player.getUniqueId());
            player.showTitle(Title.title(
                Component.text("Smash the Boss", TextColor.color(0xFF5555), TextDecoration.BOLD),
                Component.text("Dein Boss: Level " + personalLevel + "  |  /stb join", NamedTextColor.GRAY),
                Title.Times.times(
                    Duration.ofMillis(500),
                    Duration.ofSeconds(4),
                    Duration.ofMillis(1000))));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.2f);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Arena beim Verlassen automatisch aufräumen
        if (plugin.getArenaManager().hasArena(player.getUniqueId())) {
            // destroyArena ohne sendToLobby (Spieler trennt sich ja gerade)
            plugin.getArenaManager().destroyArenaOnQuit(player.getUniqueId());
        }

        plugin.getScoreboardManager().removeScoreboard(player);

        event.quitMessage(
            Component.text("§8[§c-§8] ")
                .append(Component.text(player.getName(), NamedTextColor.WHITE)));
    }
}
