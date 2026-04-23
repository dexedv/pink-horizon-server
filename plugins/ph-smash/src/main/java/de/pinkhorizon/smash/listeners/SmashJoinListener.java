package de.pinkhorizon.smash.listeners;

import de.pinkhorizon.smash.PHSmash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import de.pinkhorizon.smash.arena.ArenaInstance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

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

        // Scoreboard + Tab sofort (kein DB-Zugriff nötig)
        plugin.getScoreboardManager().giveScoreboard(player);
        plugin.getTabManager().update(player);

        // DB-Zugriffe async – blockieren nicht den Main-Thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getPlayerDataManager().ensurePlayer(player.getUniqueId(), player.getName());
            int personalLevel = plugin.getPlayerDataManager().getPersonalBossLevel(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                plugin.getUpgradeManager().applyStats(player);
                plugin.getScoreboardManager().update(player);

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.showTitle(Title.title(
                        Component.text("Smash the Boss", TextColor.color(0xFF5555), TextDecoration.BOLD),
                        Component.text("Dein Boss: Level " + personalLevel + "  |  /stb join", NamedTextColor.GRAY),
                        Title.Times.times(
                            Duration.ofMillis(500),
                            Duration.ofSeconds(4),
                            Duration.ofMillis(1000))));
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.2f);
                }, 5L);
            });
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player        player = event.getEntity();
        ArenaInstance arena  = plugin.getArenaManager().getArena(player.getUniqueId());
        if (arena == null) return;

        // Items + XP behalten
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();

        // Streak zurücksetzen
        plugin.getStreakManager().resetStreak(player.getUniqueId());

        // Eigene Tod-Nachricht statt Standard
        event.deathMessage(Component.text(
            "§c✗ §f" + player.getName() + " §7wurde vom Boss auf Level §c"
            + arena.getBossLevel() + " §7besiegt!"));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player        player = event.getPlayer();
        ArenaInstance arena  = plugin.getArenaManager().getArena(player.getUniqueId());
        if (arena == null || arena.getWorld() == null) return;

        // Respawn direkt in der Arena (nicht Welt-Spawn)
        event.setRespawnLocation(
            plugin.getArenaManager().getPlayerSpawnLocation(arena.getWorld()));
    }

    /**
     * PlayerPostRespawnEvent (Paper) feuert nachdem der Spieler vollständig in der
     * neuen Welt ist – zuverlässiger als runTaskLater(1L) nach PlayerRespawnEvent.
     */
    @EventHandler
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player        player = event.getPlayer();
        ArenaInstance arena  = plugin.getArenaManager().getArena(player.getUniqueId());
        if (arena == null || arena.getWorld() == null) return;

        // Sicherstellen, dass der Spieler wirklich in der Arena-Welt ist
        if (!player.getWorld().equals(arena.getWorld())) return;

        int bossLevel = arena.getBossLevel();
        plugin.getArenaManager().respawnBossAfterDeath(player.getUniqueId());
        plugin.getArenaManager().restoreArenaItems(player);
        plugin.getScoreboardManager().update(player);
        player.sendMessage("§c✗ §7Besiegt! Boss Level §c" + bossLevel + " §7wurde zurückgesetzt.");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.5f);
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
