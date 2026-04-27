package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.enums.TitleType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.api.events.island.IslandDeletedEvent;
import world.bentobox.bentobox.api.events.island.IslandResetEvent;

/**
 * Lauscht auf BentoBox-Island-Events und verbindet sie mit unseren Custom-Features
 * (Titel, Achievements, Scoreboard-Update).
 */
public class BentoBoxListener implements Listener {

    private final PHSkyBlock plugin;

    public BentoBoxListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    /** Neue Insel erstellt → Inselbesitzer-Titel vergeben + Starter-Generator */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent e) {
        var uuid = e.getPlayerUUID();
        if (uuid == null) return;

        // Kurze Verzögerung damit BentoBox die Insel vollständig initialisiert hat
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getAchievementManager().grantTitleOwnership(uuid, TitleType.INSELBESITZER);
            if (plugin.getTitleManager().getActiveTitle(uuid) == TitleType.KEIN_TITEL) {
                plugin.getTitleManager().silentSetActiveTitle(uuid, TitleType.INSELBESITZER);
            }
            // Scoreboard für diesen Spieler updaten
            var player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                plugin.getScoreboardManager().update(player);
                // Starter-Generator ins Inventar
                var genItem = plugin.getGeneratorManager().createGeneratorItem();
                player.getInventory().addItem(genItem);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                        + "<green>Willkommen! Du hast einen <gold><bold>Generator</bold></green> erhalten. "
                        + "<gray>Platziere ihn auf deiner Insel!"));
            }
        }, 20L);
    }

    /** Insel zurückgesetzt → Scoreboard updaten */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandReset(IslandResetEvent e) {
        var uuid = e.getPlayerUUID();
        if (uuid == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            var player = plugin.getServer().getPlayer(uuid);
            if (player != null) plugin.getScoreboardManager().update(player);
        }, 40L);
    }

    /** Insel gelöscht → Scoreboard updaten */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDeleted(IslandDeletedEvent e) {
        var uuid = e.getPlayerUUID();
        if (uuid == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            var player = plugin.getServer().getPlayer(uuid);
            if (player != null) plugin.getScoreboardManager().update(player);
        }, 20L);
    }
}
