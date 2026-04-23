package de.pinkhorizon.smash.listeners;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.arena.ArenaInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class SmashCombatListener implements Listener {

    private final PHSmash plugin;

    public SmashCombatListener(PHSmash plugin) {
        this.plugin = plugin;
    }

    /** Spieler trifft Boss → echten Schaden abfangen, virtuellen HP abziehen */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHitBoss(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ArenaInstance arena = plugin.getArenaManager().getArena(player.getUniqueId());
        if (arena == null || arena.getBossEntity() == null) return;
        if (!event.getEntity().getUniqueId().equals(arena.getBossEntity().getUniqueId())) return;

        double raw = event.getDamage();
        event.setCancelled(true);
        plugin.getArenaManager().applyDamage(player, raw);
    }

    /** Boss trifft Spieler → Defense-Multiplikator anwenden */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Entity damager = event.getDamager();

        ArenaInstance arena = plugin.getArenaManager().getArena(player.getUniqueId());
        if (arena == null || arena.getBossEntity() == null) return;
        if (!damager.getUniqueId().equals(arena.getBossEntity().getUniqueId())) return;

        double defMulti = plugin.getUpgradeManager().getDefenseMultiplier(player.getUniqueId());
        event.setDamage(event.getDamage() * defMulti);
    }

    /** Verhindert, dass Bosse durch Umweltschäden sterben */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        Entity entity = event.getEntity();
        for (ArenaInstance arena : plugin.getArenaManager().getAll()) {
            if (arena.getBossEntity() != null
                && arena.getBossEntity().getUniqueId().equals(entity.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
