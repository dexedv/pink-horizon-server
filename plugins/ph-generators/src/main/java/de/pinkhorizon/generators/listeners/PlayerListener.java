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
import org.bukkit.event.player.PlayerRespawnEvent;

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

            boolean isNewPlayer = (data == null);
            if (isNewPlayer) {
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

                // Kompass-Navigator in Slot 8 setzen
                plugin.getNavigatorGUI().giveCompass(player);

                // Stats-Hologramm wiederherstellen (falls gesetzt)
                if (finalData.hasStatsHolo()) {
                    org.bukkit.World hw = Bukkit.getWorld(finalData.getHoloWorld());
                    if (hw != null) {
                        org.bukkit.Location holoLoc = new org.bukkit.Location(
                                hw, finalData.getHoloX() + 0.5,
                                finalData.getHoloY() + 1.5,
                                finalData.getHoloZ() + 0.5);
                        plugin.getHologramManager().setStatsHolo(uuid, holoLoc);
                    }
                }
                // Ranglisten-Hologramm wiederherstellen (falls gesetzt)
                if (finalData.hasLbHolo()) {
                    org.bukkit.World lbw = Bukkit.getWorld(finalData.getLbHoloWorld());
                    if (lbw != null) {
                        org.bukkit.Location lbLoc = new org.bukkit.Location(
                                lbw, finalData.getLbHoloX() + 0.5,
                                finalData.getLbHoloY() + 1.5,
                                finalData.getLbHoloZ() + 0.5);
                        plugin.getHologramManager().setLbHolo(uuid, lbLoc);
                    }
                }

                // Tutorial für neue Spieler starten
                if (isNewPlayer || !finalData.isTutorialDone()) {
                    plugin.getTutorialManager().startTutorial(player);
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
        plugin.getHologramManager().removeStatsHolo(uuid);
        plugin.getHologramManager().removeLbHolo(uuid);
        plugin.getGeneratorManager().unloadForPlayer(data);
        plugin.getSynergyManager().remove(uuid);
        plugin.getAfkRewardManager().onQuit(uuid);
        plugin.getAchievementManager().unloadPlayer(uuid);
        plugin.getUpgradeGUI().close(uuid);
        plugin.getTutorialManager().onQuit(uuid);

        // Insel-Welt entladen (Dateien bleiben erhalten)
        plugin.getIslandWorldManager().unloadIsland(uuid);

        // Async speichern
        final PlayerData finalData = data;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getRepository().savePlayer(finalData));

        plugin.getPlayerDataMap().remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Nur in Insel-Welten eingreifen
        if (!plugin.getIslandWorldManager().isIslandWorld(player.getWorld())) return;

        org.bukkit.World islandWorld = player.getWorld();
        double spawnX = plugin.getConfig().getDouble("island.spawn-x", 0.5);
        double spawnY = plugin.getConfig().getDouble("island.spawn-y", 64.0);
        double spawnZ = plugin.getConfig().getDouble("island.spawn-z", 0.5);
        float yaw     = (float) plugin.getConfig().getDouble("island.spawn-yaw", 0.0);
        float pitch   = (float) plugin.getConfig().getDouble("island.spawn-pitch", 0.0);

        event.setRespawnLocation(new org.bukkit.Location(islandWorld, spawnX, spawnY, spawnZ, yaw, pitch));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        plugin.getAfkRewardManager().onMove(event.getPlayer());
    }
}
