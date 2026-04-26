package de.pinkhorizon.generators.listeners;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.MoneyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Lädt/speichert Spielerdaten bei Join/Quit, teleportiert zur Insel
 * und trackt Bewegungen für das AFK-System.
 */
public class PlayerListener implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public PlayerListener(PHGenerators plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Spielerdaten asynchron laden
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerData data = plugin.getRepository().loadPlayer(uuid);

            if (data == null) {
                data = new PlayerData(uuid, player.getName(), 0L, 0, System.currentTimeMillis() / 1000);
                plugin.getRepository().savePlayer(data);
            }
            data.setName(player.getName());

            final PlayerData finalData = data;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getPlayerDataMap().put(uuid, finalData);

                // Generatoren, Synergien, Achievements, Quests, AFK laden
                plugin.getGeneratorManager().loadForPlayer(finalData);
                plugin.getSynergyManager().recalculateAll(finalData);
                plugin.getAchievementManager().loadPlayer(finalData);
                plugin.getQuestManager().initPlayer(finalData);
                plugin.getAfkRewardManager().onJoin(player);

                // Offline-Einkommen berechnen
                long earned = plugin.getMoneyManager().applyOfflineIncome(finalData);
                if (earned > 0) {
                    player.sendMessage(MM.deserialize(
                            "<gold>✦ Willkommen zurück! <green>+$"
                                    + MoneyManager.formatMoney(earned)
                                    + " <gray>Offline-Einkommen (bis 8h)"));
                }

                // Insel laden und Spieler teleportieren
                plugin.getIslandWorldManager().loadAndTeleport(player);

                player.sendMessage(MM.deserialize(
                        "<light_purple>IdleForge <gray>| <yellow>Guthaben: $"
                                + MoneyManager.formatMoney(finalData.getMoney())
                                + " <gray>| <aqua>Prestige: " + finalData.getPrestige()
                                + " <gray>| <green>/gen shop"));
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = plugin.getPlayerDataMap().get(uuid);
        if (data == null) return;

        data.setLastSeen(System.currentTimeMillis() / 1000);
        plugin.getGeneratorManager().unloadForPlayer(data);
        plugin.getSynergyManager().remove(uuid);
        plugin.getAfkRewardManager().onQuit(uuid);
        plugin.getAchievementManager().unloadPlayer(uuid);
        plugin.getUpgradeGUI().close(uuid);

        // Insel-Welt entladen (Dateien bleiben erhalten)
        plugin.getIslandWorldManager().unloadIsland(uuid);

        // Async speichern
        final PlayerData finalData = data;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getRepository().savePlayer(finalData));

        plugin.getPlayerDataMap().remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        plugin.getAfkRewardManager().onMove(event.getPlayer());
    }
}
