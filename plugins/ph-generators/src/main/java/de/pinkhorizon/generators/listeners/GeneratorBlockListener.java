package de.pinkhorizon.generators.listeners;

import de.pinkhorizon.generators.GeneratorType;
import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Player;

/**
 * Erkennt das Platzieren und Abbauen von Generator-Items.
 */
public class GeneratorBlockListener implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public GeneratorBlockListener(PHGenerators plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GeneratorType type = plugin.getGeneratorManager().getGeneratorType(event.getItemInHand());
        if (type == null) return;

        // Sicherheit: Generatoren nur auf eigener Insel platzierbar
        org.bukkit.World world = event.getBlock().getWorld();
        if (!plugin.getIslandWorldManager().isOwnIsland(world, player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(MM.deserialize("<red>Generatoren können nur auf deiner eigenen Insel platziert werden!"));
            return;
        }

        boolean success = plugin.getGeneratorManager().placeGenerator(
                player, event.getBlock().getLocation(), type);

        if (!success) {
            event.setCancelled(true);
        } else {
            var data = plugin.getPlayerDataMap().get(player.getUniqueId());
            if (data != null) plugin.getQuestManager().trackPlace(data);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGeneratorManager().isGenerator(event.getBlock().getLocation())) return;

        PlacedGenerator gen = plugin.getGeneratorManager().getAt(event.getBlock().getLocation());
        if (gen == null) return;

        // Nur der Besitzer darf abbauen (oder Admin)
        if (!gen.getOwnerUUID().equals(player.getUniqueId())
                && !player.hasPermission("ph.generators.admin")) {
            event.setCancelled(true);
            player.sendMessage(MM.deserialize("<red>Das ist nicht dein Generator!"));
            return;
        }

        // Drop: Generator-Item zurückgeben
        event.setDropItems(false);
        plugin.getGeneratorManager().removeGenerator(event.getBlock().getLocation(), player.getUniqueId());
        event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation(),
                plugin.getGeneratorManager().createGeneratorItem(gen.getType(), 1));

        player.sendMessage(MM.deserialize("<yellow>Generator abgebaut. Kauf-Level-Fortschritt geht verloren!"));
    }
}
