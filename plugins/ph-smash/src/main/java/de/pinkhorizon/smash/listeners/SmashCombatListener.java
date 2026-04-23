package de.pinkhorizon.smash.listeners;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.arena.ArenaInstance;
import de.pinkhorizon.smash.managers.DailyChallengeManager;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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

    /**
     * Spieler trifft Boss (Schwert oder Bogen).
     * Echter Schaden wird abgefangen; virtuelles HP-System übernimmt.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHitBoss(EntityDamageByEntityEvent event) {
        // Spieler ermitteln – direkt (Schwert) oder über Projektil (Bogen)
        Player  player;
        boolean isBow;

        if (event.getDamager() instanceof Player p) {
            player = p;
            isBow  = false;
        } else if (event.getDamager() instanceof Projectile proj
                   && proj.getShooter() instanceof Player p) {
            player = p;
            isBow  = true;
        } else {
            return;
        }

        ArenaInstance arena = plugin.getArenaManager().getArena(player.getUniqueId());
        if (arena == null || arena.getBossEntity() == null) return;
        if (!event.getEntity().getUniqueId().equals(arena.getBossEntity().getUniqueId())) return;

        double raw = event.getDamage();
        event.setCancelled(true);

        // ── Explosivpfeil (nur Bogen) ──
        if (isBow) {
            double expChance = plugin.getAbilityManager().getExplosiveChance(player.getUniqueId());
            if (expChance > 0 && Math.random() < expChance) {
                raw *= 2.0;
                player.sendMessage("§6✦ §eExplosivpfeil!");
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
            }
        }

        // ── Berserker-Bonus (Schwert + Bogen) ──
        double berserker = plugin.getAbilityManager().getBerserkerBonus(player.getUniqueId());
        if (berserker > 0) {
            var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (hpAttr != null && player.getHealth() < hpAttr.getValue() * 0.35) {
                raw *= (1.0 + berserker);
            }
        }

        plugin.getArenaManager().applyDamage(player, raw);
    }

    /**
     * Boss trifft Spieler – Dodge + Defense-Multiplikator.
     * Funktioniert auch für Boss-Projektile (z.B. Skeleton-Pfeile).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ArenaInstance arena = plugin.getArenaManager().getArena(player.getUniqueId());
        if (arena == null || arena.getBossEntity() == null) return;

        // Direkt oder als Projektil vom Boss?
        Entity damager = event.getDamager();
        Entity boss    = arena.getBossEntity();
        boolean fromBoss = damager.getUniqueId().equals(boss.getUniqueId())
            || (damager instanceof Projectile proj
                && proj.getShooter() instanceof Entity shooter
                && shooter.getUniqueId().equals(boss.getUniqueId()));
        if (!fromBoss) return;

        // Dodge-Check
        double dodge = plugin.getAbilityManager().getDodgeChance(player.getUniqueId());
        if (dodge > 0 && Math.random() < dodge) {
            event.setCancelled(true);
            player.sendMessage("§b⚡ §7Ausgewichen!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
            plugin.getDailyChallengeManager().addProgress(player.getUniqueId(),
                DailyChallengeManager.ChallengeType.DODGE_HITS, 1);
            return;
        }

        // Boss-Schaden aus BossConfig (skaliert mit Level), dann Defense + Rage-Multiplikator anwenden
        double baseDamage = arena.getConfig().damage();
        if (arena.isRageActive()) baseDamage *= 1.5;
        double defMulti = plugin.getUpgradeManager().getDefenseMultiplier(player.getUniqueId())
            * plugin.getRuneManager().getShieldRuneMultiplier(player.getUniqueId());
        event.setDamage(baseDamage * defMulti);
    }

    /** Verhindert, dass der Boss durch Umweltschäden stirbt */
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
