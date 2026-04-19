package de.pinkhorizon.lobby.listeners;

import de.pinkhorizon.lobby.PHLobby;
import de.pinkhorizon.lobby.managers.HotbarManager;
import de.pinkhorizon.lobby.managers.ScoreboardManager;
import de.pinkhorizon.lobby.managers.TabManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;

public class JoinTitleListener implements Listener {

    private final PHLobby plugin;
    private final ScoreboardManager scoreboardManager;
    private final TabManager tabManager;

    public JoinTitleListener(PHLobby plugin, ScoreboardManager scoreboardManager, TabManager tabManager) {
        this.plugin = plugin;
        this.scoreboardManager = scoreboardManager;
        this.tabManager = tabManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Join-Nachricht im Chat
        event.joinMessage(
            Component.text("\u00a78[\u00a7a+\u00a78] ", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), TextColor.color(0xFF69B4)))
        );

        // Spielmodus + Status
        if (player.hasPermission("lobby.admin")) {
            player.setGameMode(GameMode.CREATIVE);
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setAllowFlight(true);
        }

        // Zum Spawn teleportieren
        teleportToSpawn(player);

        // Hotbar-Items
        HotbarManager.giveHotbar(player, plugin);

        // Owner-Rank für Admins automatisch setzen
        if (player.hasPermission("lobby.admin")) {
            plugin.getRankManager().setRank(player.getUniqueId(), player.getName(), "owner");
        }

        // Scoreboard
        scoreboardManager.giveScoreboard(player);

        // Tab
        tabManager.update(player);
        plugin.getRankManager().applyTabName(player);

        // Title anzeigen (1 Tick verzögert damit der Client bereit ist)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean firstJoin = !player.hasPlayedBefore();
            Component title = Component.text("Pink Horizon", TextColor.color(0xFF69B4), TextDecoration.BOLD);
            Component subtitle = firstJoin
                    ? Component.text("Willkommen auf dem Server!", NamedTextColor.GRAY)
                    : Component.text("Willkommen zurück, " + player.getName() + "!", NamedTextColor.GRAY);

            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(
                            Duration.ofMillis(500),
                            Duration.ofSeconds(3),
                            Duration.ofMillis(1000)
                    )));

            // Begrüßungs-Sound
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);

            // Action-Bar
            player.sendActionBar(Component.text(
                    "\u00a7dDrücke \u00a7fLeertaste 2x \u00a7dfür einen Doppelsprung!"));
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        scoreboardManager.removeScoreboard(player);

        event.quitMessage(
            Component.text("\u00a78[\u00a7c-\u00a78] ", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
        );
    }

    private void teleportToSpawn(Player player) {
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) return;
        Location spawn = new Location(world,
                plugin.getConfig().getDouble("spawn.x", 0.5),
                plugin.getConfig().getDouble("spawn.y", 65.0),
                plugin.getConfig().getDouble("spawn.z", 0.5),
                (float) plugin.getConfig().getDouble("spawn.yaw", 0),
                (float) plugin.getConfig().getDouble("spawn.pitch", 0));
        player.teleport(spawn);
    }
}
