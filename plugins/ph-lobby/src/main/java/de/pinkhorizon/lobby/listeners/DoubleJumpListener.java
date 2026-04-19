package de.pinkhorizon.lobby.listeners;

import de.pinkhorizon.lobby.PHLobby;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DoubleJumpListener implements Listener {

    private final PHLobby plugin;
    private final Set<UUID> canDoubleJump = new HashSet<>();

    public DoubleJumpListener(PHLobby plugin) {
        this.plugin = plugin;
    }

    // Wenn Spieler am Boden ist: Double-Jump wieder erlauben
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.isOnGround() && !player.isFlying()) {
            if (!canDoubleJump.contains(player.getUniqueId())) {
                canDoubleJump.add(player.getUniqueId());
                player.setAllowFlight(true);
            }
        }
    }

    // Doppelsprung ausführen wenn Leertaste 2x gedrückt
    @EventHandler
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        event.setCancelled(true);

        if (!canDoubleJump.contains(player.getUniqueId())) return;

        canDoubleJump.remove(player.getUniqueId());
        player.setAllowFlight(false);
        player.setFlying(false);

        // Sprung-Impuls
        Vector vel = player.getLocation().getDirection().multiply(0.8);
        vel.setY(0.9);
        player.setVelocity(vel);

        // Partikel + Sound
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.3, 0.1, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.5f);
    }
}
