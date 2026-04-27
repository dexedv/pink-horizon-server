package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;

public class IslandProtectionListener implements Listener {

    private final PHSkyBlock plugin;

    public IslandProtectionListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (checkProtection(e.getPlayer(), e.getBlock().getLocation().getBlockX(),
                e.getBlock().getLocation().getBlockZ())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (checkProtection(e.getPlayer(), e.getBlock().getLocation().getBlockX(),
                e.getBlock().getLocation().getBlockZ())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        var loc = e.getClickedBlock().getLocation();
        if (checkProtection(e.getPlayer(), loc.getBlockX(), loc.getBlockZ())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;

        var w = victim.getWorld();
        var skyWorld = plugin.getWorldManager().getSkyblockWorld();
        if (skyWorld == null || !w.equals(skyWorld)) return;

        // Kein PvP auf Inseln
        e.setCancelled(true);
    }

    /**
     * Gibt true zurück wenn die Aktion blockiert werden soll.
     * Null-World oder Nicht-Skyblock-Welt = niemals blockieren.
     */
    private boolean checkProtection(Player player, int x, int z) {
        var w = player.getWorld();
        var skyWorld = plugin.getWorldManager().getSkyblockWorld();
        if (skyWorld == null || !w.equals(skyWorld)) return false;

        // Admins dürfen immer
        if (player.hasPermission("skyblock.admin")) return false;

        // Eigene Insel suchen
        Island island = plugin.getIslandManager().getIslandAtLocation(
            new org.bukkit.Location(w, x, 64, z));

        if (island == null) {
            // Niemandes Land – nicht erlaubt
            player.sendMessage(plugin.msg("island-outside-border"));
            return true;
        }

        // Gebannt?
        if (plugin.getIslandManager().isBanned(island, player.getUniqueId())) {
            player.sendMessage(plugin.msg("island-banned"));
            return true;
        }

        // Mitglied/Owner?
        if (!island.isMember(player.getUniqueId())) {
            // Offene Insel: nur Besuchen (kein Bauen/Abbauen)
            player.sendMessage(plugin.msg("island-protected"));
            return true;
        }

        return false;
    }
}
