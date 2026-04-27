package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.managers.StarManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.entity.Player;

/**
 * Erkennt das Aufsammeln von Stern-Fragmenten und löst Belohnungen aus.
 */
public class StarListener implements Listener {

    private final PHSkyBlock plugin;

    public StarListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var tier = plugin.getStarManager().getStarTier(event.getItem().getItemStack());
        if (tier == null) return;
        plugin.getStarManager().onStarCollected(player, tier);
    }
}
