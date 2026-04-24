package de.pinkhorizon.lobby.listeners;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.lobby.PHLobby;
import de.pinkhorizon.lobby.managers.HotbarManager;
import de.pinkhorizon.lobby.managers.ScoreboardManager;
import de.pinkhorizon.lobby.managers.TabManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

        // Scoreboard + Discord-Status async laden
        scoreboardManager.giveScoreboard(player);
        scoreboardManager.loadDiscordStatus(player.getUniqueId());

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

        // Discord-Verifizierungs-Hinweis (3 s verzögert, async DB-Check)
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            boolean verified = false;
            try (Connection c = PHCore.getInstance().getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM discord_sync WHERE uuid = ? AND verified_at IS NOT NULL LIMIT 1")) {
                ps.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = ps.executeQuery()) { verified = rs.next(); }
            } catch (Exception ignored) {}

            if (!verified && player.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sendDiscordHint(player));
            }
        }, 60L); // 3 Sekunden
    }

    private static void sendDiscordHint(Player player) {
        if (!player.isOnline()) return;
        Component line = Component.text("─────────────────────────────────", TextColor.color(0xFF69B4));
        Component icon = Component.text(" \uD83D\uDCE2 ", NamedTextColor.WHITE);

        Component link = Component.text("discord.gg/j5C4h5XaK6", TextColor.color(0x5865F2), TextDecoration.UNDERLINED)
            .clickEvent(ClickEvent.openUrl("https://discord.gg/j5C4h5XaK6"))
            .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Öffnen", NamedTextColor.GRAY)));

        player.sendMessage(line);
        player.sendMessage(icon
            .append(Component.text("Du bist noch nicht im Discord verifiziert!", TextColor.color(0xFF69B4), TextDecoration.BOLD)));
        player.sendMessage(Component.text("   Tritt unserem Discord bei und verifiziere dich: ", NamedTextColor.GRAY)
            .append(link));
        player.sendMessage(Component.text("   Danach erhältst du Zugang zu exklusiven Channels.", NamedTextColor.DARK_GRAY));
        player.sendMessage(line);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
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
        scoreboardManager.removeDiscordStatus(player.getUniqueId());
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
