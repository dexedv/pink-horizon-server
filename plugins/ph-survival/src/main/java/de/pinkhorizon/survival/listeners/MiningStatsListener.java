package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.MiningStatsManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Hört auf BlockBreakEvents und loggt Erzfunde + Gesamtblöcke
 * für die XRay-Erkennung im Dashboard.
 */
public class MiningStatsListener implements Listener {

    private final PHSurvival plugin;

    public MiningStatsListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    /**
     * MONITOR-Priority + ignoreCancelled=true:
     * Nur tatsächlich abgebaute Blöcke zählen (nicht durch Claims blockierte).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block  block  = event.getBlock();
        MiningStatsManager mgr = plugin.getMiningStatsManager();

        // Jeden Block mitzählen
        mgr.incrementBlocks(player.getUniqueId(), 1);

        // Falls es ein Erz ist: detailliert loggen
        String oreType = MiningStatsManager.oreType(block.getType());
        if (oreType != null) {
            mgr.logOre(player.getUniqueId(), oreType, block.getX(), block.getY(), block.getZ());
        }
    }

    /** Beim Logout: offene Block-Zähler sofort in die DB schreiben. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getMiningStatsManager().flushPlayer(event.getPlayer().getUniqueId());
    }
}
