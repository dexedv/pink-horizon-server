package de.pinkhorizon.smash.listeners;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.boss.ActiveBoss;
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
        ActiveBoss boss = plugin.getBossManager().getActiveBoss();
        if (boss == null) return;

        // Prüfen ob das getroffene Entity der aktive Boss ist
        if (!event.getEntity().getUniqueId().equals(boss.getEntity().getUniqueId())) return;
        if (!(event.getDamager() instanceof Player player)) return;

        // Echten Schaden abfangen (Boss soll nicht wirklich Schaden nehmen)
        double raw = event.getDamage();
        event.setCancelled(true);

        // Virtuellen Schaden anwenden
        plugin.getBossManager().applyDamage(player, raw);
    }

    /** Boss trifft Spieler → Defense-Multiplikator anwenden */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossHitPlayer(EntityDamageByEntityEvent event) {
        ActiveBoss boss = plugin.getBossManager().getActiveBoss();
        if (boss == null) return;
        if (!(event.getEntity() instanceof Player player)) return;

        Entity damager = event.getDamager();
        if (!damager.getUniqueId().equals(boss.getEntity().getUniqueId())) return;

        // Defense-Upgrade anwenden
        double defMulti = plugin.getUpgradeManager().getDefenseMultiplier(player.getUniqueId());
        event.setDamage(event.getDamage() * defMulti);
    }

    /** Verhindert dass der Boss durch andere Ursachen stirbt */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        ActiveBoss boss = plugin.getBossManager().getActiveBoss();
        if (boss == null) return;
        if (!event.getEntity().getUniqueId().equals(boss.getEntity().getUniqueId())) return;
        if (event instanceof EntityDamageByEntityEvent) return; // Wird oben behandelt
        event.setCancelled(true); // Keine Umweltschäden (Feuer, Fall, etc.)
    }
}
