package de.pinkhorizon.minigames.listeners;

import de.pinkhorizon.minigames.PHMinigames;
import de.pinkhorizon.minigames.games.GameState;
import de.pinkhorizon.minigames.games.MiniGame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

public class GameListener implements Listener {

    private final PHMinigames plugin;

    public GameListener(PHMinigames plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getPlayer();
        Optional<MiniGame> game = getGameOf(dead.getUniqueId());
        if (game.isEmpty()) return;

        event.setCancelled(false);
        event.setDeathMessage(null); // Kein Standard-Tod-Text

        // Kill-Statistik fuer den Killer
        if (dead.getKiller() != null) {
            Player killer = dead.getKiller();
            String col = game.get().getType().name().toLowerCase();
            plugin.getStatsManager().incrementStat(killer.getUniqueId(), col + "_kills");
            killer.sendMessage("\u00a7aKill! +" + 1 + " Kill");
        }

        // Spieler aus dem Spiel entfernen (wird Spectator)
        game.get().handlePlayerDeath(dead.getUniqueId());

        // Respawn-Delay vermeiden
        dead.spigot().respawn();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Optional<MiniGame> game = getGameOf(event.getPlayer().getUniqueId());
        game.ifPresent(g -> g.removePlayer(event.getPlayer().getUniqueId()));
    }

    private Optional<MiniGame> getGameOf(java.util.UUID uuid) {
        return plugin.getArenaManager().getAllGames().stream()
                .filter(g -> g.getState() == GameState.RUNNING || g.getState() == GameState.COUNTDOWN)
                .filter(g -> g.getPlayers().contains(uuid))
                .findFirst();
    }
}
