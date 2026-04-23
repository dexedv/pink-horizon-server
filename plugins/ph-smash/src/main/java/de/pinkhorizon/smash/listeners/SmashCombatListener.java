package de.pinkhorizon.smash.listeners;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.arena.ArenaInstance;
import de.pinkhorizon.smash.managers.BossModifierManager.BossModifier;
import de.pinkhorizon.smash.managers.DailyChallengeManager;
import de.pinkhorizon.smash.managers.ForgeManager.ForgeEnchant;

import java.util.UUID;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import de.pinkhorizon.smash.arena.ArenaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

        UUID uuid = player.getUniqueId();

        // ── Explosivpfeil (nur Bogen) ──
        if (isBow) {
            double expChance = plugin.getAbilityManager().getExplosiveChance(uuid);
            if (expChance > 0 && Math.random() < expChance) {
                raw *= 2.0;
                player.sendMessage("§6✦ §eExplosivpfeil!");
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
            }
            // Forge: POWER (+50% bow damage)
            raw *= plugin.getForgeManager().getPowerMultiplier(uuid);
        } else {
            // Forge: SHARPNESS (+50% sword damage)
            raw *= plugin.getForgeManager().getSharpnessMultiplier(uuid);
        }

        // ── Berserker-Bonus (Schwert + Bogen) ──
        double berserker = plugin.getAbilityManager().getBerserkerBonus(uuid);
        if (berserker > 0) {
            var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (hpAttr != null && player.getHealth() < hpAttr.getValue() * 0.35) {
                raw *= (1.0 + berserker);
            }
        }

        plugin.getArenaManager().applyDamage(player, raw);

        // ── Forge: FIRE_ASPECT – set boss on fire ──
        if (plugin.getForgeManager().hasFireAspect(uuid) && arena.getBossEntity() != null) {
            arena.getBossEntity().setFireTicks(60);
        }

        // ── Forge: KNOCKBACK – small velocity push ──
        if (plugin.getForgeManager().hasKnockback(uuid) && arena.getBossEntity() != null) {
            var dir = arena.getBossEntity().getLocation().subtract(player.getLocation()).toVector().normalize();
            arena.getBossEntity().setVelocity(dir.multiply(0.4).setY(0.2));
        }
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

        // Immunität-Fähigkeit: einmaliger Check pro Treffer für alle Effekte
        double resistChance = plugin.getAbilityManager().getEffectResistChance(player.getUniqueId());
        boolean resisted = resistChance > 0 && Math.random() < resistChance;
        if (resisted) {
            player.sendMessage("§d🛡 §7Effekt resistiert!");
        } else {
            // VERGIFTET modifier: Poison II für 3s
            if (arena.getModifiers().contains(BossModifier.VERGIFTET)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1, false, true));
            }
            // BRENNEND modifier: Spieler brennt 4s
            if (arena.getModifiers().contains(BossModifier.BRENNEND)) {
                player.setFireTicks(80);
            }
            // VERDORREND modifier: Wither I für 4s
            if (arena.getModifiers().contains(BossModifier.VERDORREND)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0, false, true));
            }
            // VERLANGSAMEND modifier: Slowness III für 2s
            if (arena.getModifiers().contains(BossModifier.VERLANGSAMEND)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, true));
            }
        }
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

    /** Rechtsklick auf den Boss-Ruf-Kristall → Boss starten */
    @EventHandler
    public void onSummonItemClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack item   = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return;

        Component name = item.getItemMeta().displayName();
        if (name == null) return;
        if (!LegacyComponentSerializer.legacySection().serialize(name).contains(ArenaManager.SUMMON_ITEM_NAME)) return;

        event.setCancelled(true);
        plugin.getArenaManager().triggerBossSpawn(player);
    }
}
