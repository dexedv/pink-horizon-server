package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;

/**
 * Trackt Farming-, Fisch- und Zucht-Events für das Quest-System.
 */
public class FarmingListener implements Listener {

    private final PHSkyBlock plugin;

    public FarmingListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    /** Ernte-Events (Weizen, Karotten, Kartoffeln etc.) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        int amount = event.getItemsHarvested().stream()
            .mapToInt(i -> i.getAmount()).sum();
        if (amount > 0) {
            plugin.getQuestManager().onCropHarvest(player.getUniqueId(), amount);
        }
    }

    /** Fischen */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.getCaught() == null) return;
        plugin.getQuestManager().onFishCatch(event.getPlayer().getUniqueId());
    }

    /** Tiere züchten */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Animals)) return;
        plugin.getQuestManager().onAnimalBreed(player.getUniqueId());
    }
}
