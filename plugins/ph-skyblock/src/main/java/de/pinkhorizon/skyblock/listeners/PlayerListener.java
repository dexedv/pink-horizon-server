package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

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
                sp.setIslandId(null);
                plugin.getIslandRepository().setPlayerIslandId(player.getUniqueId(), null);
            }
        }

        // Erweiterte Systeme laden
        plugin.getGeneratorRepository().ensurePlayerExt(player.getUniqueId());
        plugin.getAchievementManager().loadPlayer(player.getUniqueId());
        plugin.getTitleManager().loadPlayer(player.getUniqueId());
        plugin.getQuestManager().loadPlayer(player.getUniqueId());
        plugin.getGeneratorManager().loadForPlayer(player.getUniqueId());

        // Gamemode setzen
        applyGameMode(player);

        // Scoreboard zeigen
        plugin.getScoreboardManager().show(player);

        // Tablist setzen
        updateTablist(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        var player = e.getPlayer();
        plugin.getScoreboardManager().remove(player);
        plugin.getQuestManager().savePlayer(player.getUniqueId());
        plugin.getGeneratorManager().saveAndUnloadForPlayer(player.getUniqueId());
        plugin.getAchievementManager().unloadPlayer(player.getUniqueId());
        plugin.getTitleManager().unloadPlayer(player.getUniqueId());
        plugin.getPlayerManager().saveAndUnload(player.getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        applyGameMode(e.getPlayer());
        updateTablist(e.getPlayer());
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

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private void applyGameMode(Player player) {
        World lobby = plugin.getWorldManager().getLobbyWorld();
        World sky   = plugin.getWorldManager().getSkyblockWorld();
        if (lobby != null && player.getWorld().equals(lobby)) {
            player.setGameMode(GameMode.ADVENTURE);
        } else if (sky != null && player.getWorld().equals(sky)) {
            if (player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.CREATIVE) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    private void updateTablist(Player player) {
        SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        Island island = (sp != null && sp.getIslandId() != null)
            ? plugin.getIslandManager().getIslandById(sp.getIslandId())
            : null;

        Component header = MM.deserialize(
            "\n<gradient:#ff69b4:#da70d6><bold>✦ Pink Horizon – SkyBlock ✦</bold></gradient>\n"
        );

        String inselInfo = island != null
            ? "<gray>Insel-Level: <gold>" + island.getLevel()
              + "  <gray>Score: <white>" + island.getScore()
            : "<gray>Noch keine Insel – <yellow>/is create";

        Component footer = MM.deserialize(
            "\n" + inselInfo + "\n"
            + "<gray>Online: <green>" + org.bukkit.Bukkit.getOnlinePlayers().size()
            + "  <gray>Server: <light_purple>play.pinkhorizon.fun\n"
        );

        player.sendPlayerListHeaderAndFooter(header, footer);
    }
}
