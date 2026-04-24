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
import org.bukkit.Sound;
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

        // Scoreboard
        scoreboardManager.giveScoreboard(player);

        // Tab
        tabManager.update(player);
        plugin.getRankManager().applyTabName(player);

        // Title + Rank-Sound (1 Tick verzögert)
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

            // Rang-abhängiger Join-Sound
            String rankId = plugin.getRankManager().getRankId(player.getUniqueId());
            playRankSound(player, rankId);

            // Action-Bar
            player.sendActionBar(Component.text(
                    "\u00a7dDrücke \u00a7fLeertaste 2x \u00a7dfür einen Doppelsprung!"));
        }, 5L);
    }

    private void playRankSound(Player player, String rankId) {
        Sound  sound  = Sound.ENTITY_PLAYER_LEVELUP;
        float  volume = 0.7f;
        float  pitch  = 1.2f;

        switch (rankId) {
            case "owner"     -> { sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;  volume = 1.0f; pitch = 1.0f; }
            case "admin"     -> { sound = Sound.BLOCK_BEACON_POWER_SELECT;    volume = 0.8f; pitch = 1.1f; }
            case "dev"       -> { sound = Sound.ENTITY_ENDERMAN_TELEPORT;      volume = 0.4f; pitch = 1.4f; }
            case "moderator" -> { sound = Sound.BLOCK_BEACON_ACTIVATE;        volume = 0.6f; pitch = 1.2f; }
            case "supporter" -> { sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP; volume = 0.8f; pitch = 1.3f; }
            case "vip"       -> { sound = Sound.ENTITY_VILLAGER_CELEBRATE;    volume = 0.7f; pitch = 1.2f; }
            default          -> { sound = Sound.ENTITY_PLAYER_LEVELUP;        volume = 0.7f; pitch = 1.2f; }
        }

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        scoreboardManager.removeScoreboard(player);
        plugin.getCosmeticsManager().removePlayer(player.getUniqueId());

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
