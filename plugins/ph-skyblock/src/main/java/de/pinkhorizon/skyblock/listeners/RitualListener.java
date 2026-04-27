package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Player;

/**
 * Prüft nach jedem Block-Platzieren ob ein Ritual-Muster abgeschlossen wurde.
 */
public class RitualListener implements Listener {

    private final PHSkyBlock plugin;

    public RitualListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        plugin.getRitualManager().checkRitual(player, event.getBlock().getLocation());
    }
}
