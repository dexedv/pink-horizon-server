package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Auslöser für Job-Boni: Shift+F (Schleichen + Tauschen-Taste).
 * Normales F-Drücken bleibt vollständig unberührt.
 */
public class JobBonusListener implements Listener {

    private final PHSurvival plugin;

    public JobBonusListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;   // normales F → unverändert durchlassen
        event.setCancelled(true);           // Shift+F → Bonus aktivieren, nichts tauschen
        plugin.getJobBonusManager().tryActivate(player);
    }
}
