package de.pinkhorizon.survival.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;

import java.util.Set;

public class PortalProtectionListener implements Listener {

    private static final Set<Material> PROTECTED = Set.of(
        Material.OBSIDIAN,
        Material.CRYING_OBSIDIAN,
        Material.NETHER_PORTAL,
        Material.END_PORTAL,
        Material.END_PORTAL_FRAME,
        Material.END_GATEWAY
    );

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (PROTECTED.contains(event.getBlock().getType())) {
            event.setCancelled(true);
            player.sendMessage("§cDu kannst diesen Block nicht abbauen!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        // Verhindert zufälliges Löschen von Nether-Portalen durch Spieler ohne OP
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) return;
        Player player = event.getPlayer();
        if (player == null || player.isOp()) return;
        // Nur schützen wenn direkt neben Portal-Rahmen gebaut wird
        // Erlaubt Portalbau generell, blockiert nur Zerstörung per Abbau
    }
}
