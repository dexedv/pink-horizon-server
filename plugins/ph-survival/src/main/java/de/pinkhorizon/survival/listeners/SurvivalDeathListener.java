package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurvivalDeathListener implements Listener {

    public static final Map<UUID, Location> lastDeaths = new HashMap<>();

    private final PHSurvival plugin;

    public SurvivalDeathListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        lastDeaths.put(player.getUniqueId(), player.getLocation().clone());

        if (plugin.getUpgradeManager().hasActiveKI(player.getUniqueId())) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            player.sendMessage("§aKeepInventory: §fDu hast dein Inventar behalten!");
        }
    }
}
