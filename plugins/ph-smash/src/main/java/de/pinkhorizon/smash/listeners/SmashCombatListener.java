package de.pinkhorizon.smash.listeners;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.arena.ArenaInstance;
import de.pinkhorizon.smash.managers.BossModifierManager.BossModifier;
import de.pinkhorizon.smash.managers.CombatLogManager;
import de.pinkhorizon.smash.managers.DailyChallengeManager;
import de.pinkhorizon.smash.managers.ForgeManager.ForgeEnchant;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
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
import de.pinkhorizon.smash.arena.ArenaManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SmashCombatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY            = LegacyComponentSerializer.legacySection();
    private static final double                    FIREBALL_BASE_DMG = 20.0;
    private static final long                      FIREBALL_CD_MS    = 2000L;

    private final PHSmash         plugin;
    private final Map<UUID, Long> fireballCooldowns = new HashMap<>();

    public SmashCombatListener(PHSmash plugin) {
        this.plugin = plugin;
    }

    public void clearCooldown(UUID uuid) {
        fireballCooldowns.remove(uuid);
    }

    // ── Effektiver Gesamt-Multiplikator (wie in applyDamage) ────────────────

    private double effectiveMulti(UUID uuid, ArenaInstance arena) {
        double gepanzert = arena.getModifiers().contains(BossModifier.GEPANZERT) ? 0.70 : 1.0;
        return gepanzert
            * plugin.getUpgradeManager().getAttackMultiplier(uuid)
            * plugin.getPrestigeManager().getPrestigeMultiplier(uuid)
            * plugin.getStreakManager().getStreakMultiplier(uuid)
            * plugin.getRuneManager().getWarRuneMultiplier(uuid)
            * plugin.getComboManager().getMultiplier(uuid);
    }

    /**
     * Spieler trifft Boss – Schaden & alle Fähigkeits-Procs.
     * Echter Bukkit-Schaden wird abgefangen; virtuelles HP-System übernimmt.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHitBoss(EntityDamageByEntityEvent event) {
        Player  player;
        boolean isBow           = false;
        boolean isAxe           = false;
        boolean isFireball      = false;
        boolean isFireballStick = false;

        if (event.getDamager() instanceof Player p) {
            player = p;
            ItemStack held = p.getInventory().getItemInMainHand();
            if (held.getType() == Material.STICK && held.hasItemMeta()) {
                Component nm = held.getItemMeta().displayName();
                if (nm != null && LEGACY.serialize(nm).contains(ArenaManager.FIREBALL_STICK_NAME)) {
                    isFireballStick = true;
                }
            }
            if (!isFireballStick) {
                isAxe = held.getType() == Material.DIAMOND_AXE;
            }
        } else if (event.getDamager() instanceof SmallFireball fb
                   && fb.getShooter() instanceof Player p) {
            player = p;
            isFireball = true;
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

        double raw  = event.getDamage();
        event.setCancelled(true);

        UUID              uuid = player.getUniqueId();
        CombatLogManager  log  = plugin.getCombatLogManager();
        double            multi = effectiveMulti(uuid, arena);

        // ── Feuerball-Handler ──────────────────────────────────────────────
        if (isFireball) {
            raw = FIREBALL_BASE_DMG
                * plugin.getAbilityManager().getFireballPowerMultiplier(uuid)
                * plugin.getForgeManager().getPowerMultiplier(uuid);

            double fbBerserker = plugin.getAbilityManager().getBerserkerBonus(uuid);
            if (fbBerserker > 0) {
                var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
                if (hpAttr != null && player.getHealth() < hpAttr.getValue() * 0.35) {
                    raw *= (1.0 + fbBerserker);
                }
            }

            plugin.getArenaManager().applyDamage(player, raw);
            plugin.getDailyChallengeManager().addProgress(uuid,
                DailyChallengeManager.ChallengeType.FIREBALL_HITS, 1);

            // Doppelfeuer: zweiter Feuerball (80% Schaden)
            double doppelChance = plugin.getAbilityManager().getDoppelfeuerChance(uuid);
            if (doppelChance > 0 && Math.random() < doppelChance) {
                double echoDmg = raw * 0.80;
                plugin.getArenaManager().applyDamage(player, echoDmg);
                log.proc(player, "🔥", "§6", "Doppelfeuer",
                    "×80%", echoDmg * multi);
                player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.3f);
            }

            // Verbrennung: Brand-DOT (3 Ticks × 8% des Treffers)
            double verbChance = plugin.getAbilityManager().getVerbrennungChance(uuid);
            if (verbChance > 0 && Math.random() < verbChance) {
                final double        tickDmg = raw * 0.08;
                final ArenaInstance pArena  = arena;
                final Player        pPlayer = player;
                for (int tick = 1; tick <= 3; tick++) {
                    final long delay = tick * 20L;
                    pArena.addDotTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (pArena.getBossEntity() != null && pArena.getBossEntity().isValid())
                            plugin.getArenaManager().applyDamage(pPlayer, tickDmg);
                    }, delay));
                }
                log.dot(player, "🔥", "§c", "Verbrennung", tickDmg * multi, 3);
                player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_HURT, 0.5f, 1.5f);
            }

            log.updateActionBar(player);
            return;
        }

        // ── Feuerball-Stab (Linksklick direkt auf Boss) ───────────────────
        if (isFireballStick) {
            long now  = System.currentTimeMillis();
            Long last = fireballCooldowns.get(uuid);
            if (last != null && now - last < FIREBALL_CD_MS) {
                long remMs = FIREBALL_CD_MS - (now - last);
                player.sendActionBar(LEGACY.deserialize(
                    "§c⏳ §7Feuerball bereit in §c" + String.format("%.1f", remMs / 1000.0) + "s"));
                return;
            }
            fireballCooldowns.put(uuid, now);
            org.bukkit.Location from = player.getEyeLocation();
            org.bukkit.util.Vector dir = arena.getBossEntity().getLocation().add(0, 1, 0)
                .toVector().subtract(from.toVector()).normalize();
            SmallFireball fb = player.getWorld().spawn(from, SmallFireball.class);
            fb.setShooter(player);
            fb.setDirection(dir);
            fb.setYield(0f);
            fb.setIsIncendiary(false);
            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
            return;
        }

        // ── Bogen ─────────────────────────────────────────────────────────
        if (isBow) {
            // Explosivpfeil: ×2 Schaden
            double expChance = plugin.getAbilityManager().getExplosiveChance(uuid);
            if (expChance > 0 && Math.random() < expChance) {
                double rawBefore = raw;
                raw *= 2.0;
                log.proc(player, "✦", "§e", "Explosivpfeil",
                    "×2.0", (raw - rawBefore) * multi);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
            }
            raw *= plugin.getForgeManager().getPowerMultiplier(uuid);
            raw *= plugin.getAbilityManager().getBowPowerMultiplier(uuid);
        } else {
            // Forge: Schärfe
            raw *= plugin.getForgeManager().getSharpnessMultiplier(uuid);

            if (!isAxe) {
                // Kritischer Treffer (Schwert): ×2.5
                double critChance = plugin.getAbilityManager().getCritChance(uuid);
                if (critChance > 0 && Math.random() < critChance) {
                    double rawBefore = raw;
                    raw *= 2.5;
                    log.proc(player, "✦", "§c", "Kritischer Treffer",
                        "×2.5  §8(§7Crit-Chance §c" + (int)(critChance * 100) + "%§8)",
                        (raw - rawBefore) * multi);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
                }

                // Hinrichtung (Boss < 25% HP)
                double exeBonus = plugin.getAbilityManager().getExecuteBonus(uuid);
                if (exeBonus > 0 && arena.getHpPercent() < 0.25) {
                    double rawBefore = raw;
                    raw *= (1.0 + exeBonus);
                    int bossPct = (int)(arena.getHpPercent() * 100);
                    log.proc(player, "⚔", "§4", "Hinrichtung",
                        "+" + (int)(exeBonus * 100) + "%  §8(§7Boss bei §c" + bossPct + "%§8)",
                        (raw - rawBefore) * multi);
                }
            }
        }

        // Berserker (Schwert + Bogen): eigener Multiplikator bei <35% HP
        double berserker = plugin.getAbilityManager().getBerserkerBonus(uuid);
        if (berserker > 0) {
            var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (hpAttr != null && player.getHealth() < hpAttr.getValue() * 0.35) {
                raw *= (1.0 + berserker);
                // Berserker ist ein dauerhafter Bonus → kein eigenes proc-Log, zeigt im HUD
            }
        }

        plugin.getArenaManager().applyDamage(player, raw);

        // Wirbelwind: Doppel-Treffer (nur Schwert, 50% Schaden)
        if (!isBow && !isAxe) {
            double wwChance = plugin.getAbilityManager().getWhirlwindChance(uuid);
            if (wwChance > 0 && Math.random() < wwChance) {
                double echoDmg = raw * 0.5;
                plugin.getArenaManager().applyDamage(player, echoDmg);
                log.proc(player, "⚡", "§6", "Wirbelwind",
                    "×50%", echoDmg * multi);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
            }
        }

        // Mehrfachschuss + Giftpfeil (Bogen)
        if (isBow) {
            double msChance = plugin.getAbilityManager().getMultishotChance(uuid);
            if (msChance > 0 && Math.random() < msChance) {
                double echoDmg = raw * 0.8;
                plugin.getArenaManager().applyDamage(player, echoDmg);
                log.proc(player, "🏹", "§e", "Mehrfachschuss",
                    "×80%", echoDmg * multi);
                player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT, 1f, 1.5f);
            }

            double poisonChance = plugin.getAbilityManager().getPoisonChance(uuid);
            if (poisonChance > 0 && Math.random() < poisonChance) {
                final double        tickDmg = raw * 0.05;
                final ArenaInstance pArena  = arena;
                final Player        pPlayer = player;
                for (int tick = 1; tick <= 3; tick++) {
                    final long delay = tick * 20L;
                    pArena.addDotTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (pArena.getBossEntity() != null && pArena.getBossEntity().isValid())
                            plugin.getArenaManager().applyDamage(pPlayer, tickDmg);
                    }, delay));
                }
                log.dot(player, "☠", "§2", "Giftpfeil", tickDmg * multi, 3);
                player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_HURT, 0.5f, 1.5f);
            }
        }

        // Blutungs-DOT (Axt)
        if (isAxe) {
            double blutungChance = plugin.getAbilityManager().getBlutungChance(uuid);
            if (blutungChance > 0 && Math.random() < blutungChance) {
                int    ticks    = plugin.getAbilityManager().getBlutungTicks(uuid);
                double klafFact = plugin.getAbilityManager().getKlaffendeWundeFactor(uuid);
                final double    tickDmg = raw * 0.06 * klafFact;
                final ArenaInstance pArena  = arena;
                final Player        pPlayer = player;
                for (int tick = 1; tick <= ticks; tick++) {
                    final long delay = tick * 20L;
                    pArena.addDotTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (pArena.getBossEntity() != null && pArena.getBossEntity().isValid())
                            plugin.getArenaManager().applyDamage(pPlayer, tickDmg);
                    }, delay));
                }
                log.dot(player, "🩸", "§c", "Blutung", tickDmg * multi, ticks);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.4f);
                plugin.getDailyChallengeManager().addProgress(uuid,
                    DailyChallengeManager.ChallengeType.AXE_BLEED, 1);
            }
        }

        // Forge: Feueraspekt
        if (plugin.getForgeManager().hasFireAspect(uuid) && arena.getBossEntity() != null) {
            arena.getBossEntity().setFireTicks(60);
        }

        // Forge: Rückstoß
        if (plugin.getForgeManager().hasKnockback(uuid) && arena.getBossEntity() != null) {
            var dir = arena.getBossEntity().getLocation().subtract(player.getLocation()).toVector().normalize();
            arena.getBossEntity().setVelocity(dir.multiply(0.4).setY(0.2));
        }

        log.updateActionBar(player);
    }

    /**
     * Boss trifft Spieler – Dodge, Defense-Multiplikator, Modifier-Effekte.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ArenaInstance arena = plugin.getArenaManager().getArena(player.getUniqueId());
        if (arena == null || arena.getBossEntity() == null) return;

        Entity  damager  = event.getDamager();
        Entity  boss     = arena.getBossEntity();
        boolean fromBoss = damager.getUniqueId().equals(boss.getUniqueId())
            || (damager instanceof Projectile proj
                && proj.getShooter() instanceof Entity shooter
                && shooter.getUniqueId().equals(boss.getUniqueId()));
        if (!fromBoss) return;

        CombatLogManager log = plugin.getCombatLogManager();

        // Dodge-Check
        double dodge = plugin.getAbilityManager().getDodgeChance(player.getUniqueId());
        if (dodge > 0 && Math.random() < dodge) {
            event.setCancelled(true);
            log.dodge(player, dodge);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
            plugin.getDailyChallengeManager().addProgress(player.getUniqueId(),
                DailyChallengeManager.ChallengeType.DODGE_HITS, 1);
            return;
        }

        // Schaden skalieren
        double baseDamage = arena.getConfig().damage();
        if (arena.isRageActive()) baseDamage *= 1.5;
        double defMulti = plugin.getUpgradeManager().getDefenseMultiplier(player.getUniqueId())
            * plugin.getRuneManager().getShieldRuneMultiplier(player.getUniqueId());
        event.setDamage(baseDamage * defMulti);

        // Immun-Chance
        double resistChance = plugin.getAbilityManager().getEffectResistChance(player.getUniqueId());
        boolean resisted    = resistChance > 0 && Math.random() < resistChance;
        if (resisted) {
            log.resist(player, resistChance);
        } else {
            if (arena.getModifiers().contains(BossModifier.VERGIFTET)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1, false, true));
                log.modifierPoison(player);
            }
            if (arena.getModifiers().contains(BossModifier.BRENNEND)) {
                player.setFireTicks(80);
                log.modifierBurn(player);
            }
            if (arena.getModifiers().contains(BossModifier.VERDORREND)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0, false, true));
                log.modifierWither(player);
            }
            if (arena.getModifiers().contains(BossModifier.VERLANGSAMEND)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, true));
                log.modifierSlow(player);
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

    /** Linksklick mit Feuerball-Stab → SmallFireball in Richtung Boss */
    @EventHandler
    public void onFireballStickClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack item   = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.STICK || !item.hasItemMeta()) return;

        Component name = item.getItemMeta().displayName();
        if (name == null) return;
        if (!LEGACY.serialize(name).contains(ArenaManager.FIREBALL_STICK_NAME)) return;

        event.setCancelled(true);

        ArenaInstance arena = plugin.getArenaManager().getArena(player.getUniqueId());
        if (arena == null || arena.getBossEntity() == null) return;

        long now  = System.currentTimeMillis();
        Long last = fireballCooldowns.get(player.getUniqueId());
        if (last != null && now - last < FIREBALL_CD_MS) {
            long remMs = FIREBALL_CD_MS - (now - last);
            player.sendActionBar(LEGACY.deserialize(
                "§c⏳ §7Feuerball bereit in §c" + String.format("%.1f", remMs / 1000.0) + "s"));
            return;
        }
        fireballCooldowns.put(player.getUniqueId(), now);

        org.bukkit.Location from = player.getEyeLocation();
        org.bukkit.util.Vector dir = arena.getBossEntity().getLocation().add(0, 1, 0)
            .toVector().subtract(from.toVector()).normalize();

        SmallFireball fb = player.getWorld().spawn(from, SmallFireball.class);
        fb.setShooter(player);
        fb.setDirection(dir);
        fb.setYield(0f);
        fb.setIsIncendiary(false);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
    }

    /** Rechtsklick auf Boss-Ruf-Kristall → Boss starten */
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
        if (!LEGACY.serialize(name).contains(ArenaManager.SUMMON_ITEM_NAME)) return;

        event.setCancelled(true);
        plugin.getArenaManager().triggerBossSpawn(player);
    }
}
