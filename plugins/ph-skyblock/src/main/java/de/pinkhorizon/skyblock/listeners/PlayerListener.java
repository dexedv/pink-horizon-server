package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final PHSkyBlock plugin;

    public PlayerListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        var player = e.getPlayer();
        SkyPlayer sp = plugin.getPlayerManager().loadPlayer(player.getUniqueId(), player.getName());

        // Insel laden (wenn vorhanden, in Cache)
        if (sp.getIslandId() != null) {
            Island island = plugin.getIslandManager().getIslandById(sp.getIslandId());
            if (island == null) {
                // Inkonsistenz – Referenz löschen
                sp.setIslandId(null);
                plugin.getIslandRepository().setPlayerIslandId(player.getUniqueId(), null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.getPlayerManager().saveAndUnload(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        var player = e.getPlayer();
        var skyWorld = plugin.getWorldManager().getSkyblockWorld();
        // Bei Tod in der Skyblock-Welt → Respawn am Spawn
        if (skyWorld != null && player.getWorld().equals(skyWorld)) {
            var spawnCfg = plugin.getConfig().getConfigurationSection("spawn");
            if (spawnCfg != null) {
                var spawnWorld = org.bukkit.Bukkit.getWorld(
                    spawnCfg.getString("world", "world"));
                if (spawnWorld == null) spawnWorld = plugin.getWorldManager().getLobbyWorld();
                if (spawnWorld != null) {
                    e.setRespawnLocation(new org.bukkit.Location(
                        spawnWorld,
                        spawnCfg.getDouble("x", 0.5),
                        spawnCfg.getDouble("y", 65.0),
                        spawnCfg.getDouble("z", 0.5)
                    ));
                }
            }
        }
    }
}
