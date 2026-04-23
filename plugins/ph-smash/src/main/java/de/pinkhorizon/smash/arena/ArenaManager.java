package de.pinkhorizon.smash.arena;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.boss.BossConfig;
import de.pinkhorizon.smash.managers.BossModifierManager;
import de.pinkhorizon.smash.managers.BossModifierManager.BossModifier;
import de.pinkhorizon.smash.managers.DailyChallengeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import de.pinkhorizon.smash.listeners.SmashNavigatorListener;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.*;

public class ArenaManager {

    private final PHSmash                    plugin;
    private final Map<UUID, ArenaInstance>   arenas = new HashMap<>();

    public ArenaManager(PHSmash plugin) {
        this.plugin = plugin;
        cleanupOrphanedArenas();
    }

    // ── Öffentliche API ────────────────────────────────────────────────────

    public void createArena(Player player) {
        UUID uuid = player.getUniqueId();

        if (arenas.containsKey(uuid)) {
            ArenaInstance existing = arenas.get(uuid);
            if (existing.getWorld() != null) {
                player.teleport(getPlayerSpawn(existing.getWorld()));
            }
            player.sendMessage("§eDu bist bereits in einer Arena.");
            return;
        }

        int    bossLevel = plugin.getPlayerDataManager().getPersonalBossLevel(uuid);
        String worldName = "arena_" + uuid.toString().replace("-", "").substring(0, 8);
        ArenaInstance arena = new ArenaInstance(uuid, worldName, bossLevel);
        arenas.put(uuid, arena);

        player.sendMessage("§7Bereite Arena vor (Boss Level §c" + bossLevel + "§7)§8...");

        // Welt async kopieren, dann auf Main-Thread laden + spawnen
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                copyTemplateWorld(worldName);
            } catch (IOException e) {
                plugin.getLogger().severe("Arena-Kopie fehlgeschlagen für " + player.getName() + ": " + e.getMessage());
                arenas.remove(uuid);
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§cFehler beim Erstellen der Arena!"));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !arenas.containsKey(uuid)) {
                    deleteWorldAsync(worldName);
                    return;
                }

                World world = new WorldCreator(worldName).createWorld();
                if (world == null) {
                    arenas.remove(uuid);
                    player.sendMessage("§cFehler beim Laden der Arena-Welt!");
                    return;
                }

                world.setDifficulty(Difficulty.HARD);
                world.setGameRule(GameRule.DO_MOB_SPAWNING,  false);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE,  false);
                world.setGameRule(GameRule.DO_FIRE_TICK,      false);
                world.setGameRule(GameRule.KEEP_INVENTORY,    true);
                world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
                world.setTime(6000);

                // WorldBorder – 35 Blöcke Radius, zentriert auf Boss-Spawn
                Location bossLoc = getBossSpawn(world);
                var border = world.getWorldBorder();
                border.setCenter(bossLoc.getX(), bossLoc.getZ());
                border.setSize(70);          // Radius 35 = Durchmesser 70
                border.setDamageBuffer(0);   // sofort Schaden außerhalb der Linie
                border.setDamageAmount(2.0); // 2 HP/s außerhalb
                border.setWarningDistance(5);
                border.setWarningTime(0);

                arena.setWorld(world);
                spawnBossInArena(arena);
                player.teleport(getPlayerSpawn(world));
                giveArenaItems(player);
                player.sendMessage("§a✔ §7Arena bereit! Besieg Boss Level §c" + arena.getBossLevel() + "§7!");
                plugin.getScoreboardManager().update(player);
            });
        });
    }

    public void destroyArena(UUID playerUuid) {
        ArenaInstance arena = arenas.remove(playerUuid);
        if (arena == null) return;

        // Teleport player out BEFORE unloading – unloadWorld fails silently if players are still inside
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            teleportToHub(player);
        }

        cleanupArenaResources(arena);
    }

    /** Schaden auf Boss des Spielers anwenden (vom CombatListener aufgerufen) */
    public void applyDamage(Player player, double rawDamage) {
        ArenaInstance arena = arenas.get(player.getUniqueId());
        if (arena == null) return;

        UUID uuid = player.getUniqueId();

        // GEPANZERT modifier: -30% damage to boss
        if (arena.getModifiers().contains(BossModifier.GEPANZERT)) {
            rawDamage *= 0.70;
        }

        double multiplier = plugin.getUpgradeManager().getAttackMultiplier(uuid)
            * plugin.getPrestigeManager().getPrestigeMultiplier(uuid)
            * plugin.getStreakManager().getStreakMultiplier(uuid)
            * plugin.getRuneManager().getWarRuneMultiplier(uuid)
            * plugin.getComboManager().getMultiplier(uuid);
        double real = rawDamage * multiplier;
        arena.applyDamage(real);

        // GESPIEGELT modifier: 10% damage reflected to player
        if (arena.getModifiers().contains(BossModifier.GESPIEGELT)) {
            double reflected = real * 0.10;
            double newHp = Math.max(0.5, player.getHealth() - reflected);
            player.setHealth(newHp);
        }

        // Lifesteal (base + forge lifedrain bonus)
        double lifesteal = plugin.getUpgradeManager().getLifestealPercent(uuid)
            + plugin.getForgeManager().getLifedrainBonus(uuid);
        if (lifesteal > 0) {
            var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (hpAttr != null) {
                double healed = Math.min(player.getHealth() + real * lifesteal, hpAttr.getValue());
                player.setHealth(healed);
            }
        }

        updateBossBar(arena);
        checkBossPhases(player, arena);

        // XP bar = boss HP (level = boss level, exp = HP %)
        player.setLevel(arena.getBossLevel());
        player.setExp((float) Math.max(0f, Math.min(1f, arena.getHpPercent())));

        plugin.getScoreboardManager().update(player);

        if (arena.isDead()) {
            onBossDefeated(player, arena);
        }
    }

    /** Boss-Level erzwingen (Admin-Befehl) */
    public void forceBoss(Player player, int level) {
        ArenaInstance arena = arenas.get(player.getUniqueId());
        if (arena == null) { player.sendMessage("§cDu bist in keiner Arena!"); return; }

        if (arena.getBossEntity() != null && arena.getBossEntity().isValid()) arena.getBossEntity().remove();
        if (arena.getBossBar()   != null)  arena.getBossBar().removeAll();

        arena.resetForNextBoss(level);
        plugin.getPlayerDataManager().setPersonalBossLevel(player.getUniqueId(), level);
        spawnBossInArena(arena);
        plugin.getScoreboardManager().update(player);
        player.sendMessage("§a✔ §7Boss auf Level §c" + level + " §7gesetzt.");
    }

    /** Wie destroyArena, aber ohne sendToLobby (Spieler trennt sich gerade) */
    public void destroyArenaOnQuit(UUID playerUuid) {
        ArenaInstance arena = arenas.remove(playerUuid);
        if (arena != null) cleanupArenaResources(arena);
    }

    public boolean       hasArena(UUID uuid)   { return arenas.containsKey(uuid); }
    public ArenaInstance getArena(UUID uuid)   { return arenas.get(uuid); }
    public Collection<ArenaInstance> getAll()  { return Collections.unmodifiableCollection(arenas.values()); }

    /** Nach Spieler-Tod: gleichen Boss-Level neu spawnen, Session-Schaden reset */
    public void respawnBossAfterDeath(UUID playerUuid) {
        ArenaInstance arena = arenas.get(playerUuid);
        if (arena == null) return;
        if (arena.getBossEntity() != null && arena.getBossEntity().isValid()) arena.getBossEntity().remove();
        if (arena.getBossBar()   != null) arena.getBossBar().removeAll();
        arena.resetForNextBoss(arena.getBossLevel());
        spawnBossInArena(arena);
    }

    /** Gibt dem Spieler alle Arena-Items erneut (nach Tod / Reset) */
    public void restoreArenaItems(Player player) {
        giveArenaItems(player);
    }

    /** Spieler-Spawn-Position für eine Arena-Welt (für Respawn-Event) */
    public Location getPlayerSpawnLocation(World world) {
        return getPlayerSpawn(world);
    }

    /** Beim Plugin-Disable: alle Arenen synchron aufräumen */
    public void destroyAll() {
        for (ArenaInstance arena : new ArrayList<>(arenas.values())) {
            if (arena.getTargetTask()   != null) arena.getTargetTask().cancel();
            if (arena.getRegenTask()    != null) arena.getRegenTask().cancel();
            if (arena.getExplosivTask() != null) arena.getExplosivTask().cancel();
            if (arena.getBossEntity() != null && arena.getBossEntity().isValid()) arena.getBossEntity().remove();
            if (arena.getBossBar()   != null) arena.getBossBar().removeAll();
            World world = arena.getWorld();
            if (world != null) {
                String name = world.getName();
                Bukkit.unloadWorld(world, false);
                try { deleteDirectory(new File(Bukkit.getWorldContainer(), name).toPath()); }
                catch (IOException e) { plugin.getLogger().warning("Arena-Welt nicht gelöscht: " + name); }
            }
        }
        arenas.clear();
    }

    // ── Boss-Logik ─────────────────────────────────────────────────────────

    private void spawnBossInArena(ArenaInstance arena) {
        BossConfig cfg    = arena.getConfig();
        World      world  = arena.getWorld();
        Location   loc    = getBossSpawn(world);

        // Roll boss modifiers
        var modifiers = plugin.getBossModifierManager().rollModifiers(cfg.level());
        arena.setModifiers(modifiers);

        // Preview title before spawn
        Player previewPlayer = Bukkit.getPlayer(arena.getPlayerUuid());
        if (previewPlayer != null) {
            String modBar = plugin.getBossModifierManager().buildModifierBar(modifiers);
            previewPlayer.showTitle(Title.title(
                Component.text(cfg.displayName().replaceAll("§.", ""), TextColor.color(0xFF5555), TextDecoration.BOLD),
                Component.text("Level " + cfg.level() + (modBar.isEmpty() ? "" : "  " + modBar), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }

        LivingEntity entity = (LivingEntity) world.spawnEntity(loc, getBossEntityType(cfg.level()));

        entity.setCustomName(cfg.displayName());
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setGlowing(true);

        var hpAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(1024);
            entity.setHealth(1024);
        }

        // RASEND modifier: +50% movement speed
        if (modifiers.contains(BossModifier.RASEND) && entity instanceof Mob) {
            var speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.5);
        }

        // Alte Bar entfernen bevor neue erstellt wird (verhindert Bar-Leak)
        if (arena.getBossBar() != null) arena.getBossBar().removeAll();

        BossBar bar = Bukkit.createBossBar(
            buildBarTitle(cfg, cfg.maxHp(), modifiers),
            BarColor.GREEN, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);

        Player owner = Bukkit.getPlayer(arena.getPlayerUuid());
        if (owner != null) {
            bar.addPlayer(owner);
            if (entity instanceof Mob mob) mob.setTarget(owner);
        }

        arena.setBossEntity(entity);
        arena.setBossBar(bar);

        // REGENERIEREND modifier: heal 0.5% max HP per second
        if (modifiers.contains(BossModifier.REGENERIEREND)) {
            if (arena.getRegenTask() != null) arena.getRegenTask().cancel();
            double healPerTick = cfg.maxHp() * 0.005;
            var regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (arena.getBossEntity() == null || !arena.getBossEntity().isValid()) return;
                if (!arena.isDead()) {
                    arena.heal(healPerTick);
                    updateBossBar(arena);
                    Player p = Bukkit.getPlayer(arena.getPlayerUuid());
                    if (p != null && !p.isDead()) {
                        p.setExp((float) Math.max(0f, Math.min(1f, arena.getHpPercent())));
                    }
                }
            }, 20L, 20L);
            arena.setRegenTask(regenTask);
        }

        // EXPLOSIV modifier: alle 8s Explosion am Spieler
        if (modifiers.contains(BossModifier.EXPLOSIV)) {
            if (arena.getExplosivTask() != null) arena.getExplosivTask().cancel();
            double explosionDmg = Math.min(2.0 + cfg.level() * 0.04, 8.0);
            var explosivTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (arena.getBossEntity() == null || !arena.getBossEntity().isValid()) return;
                if (arena.isDead()) return;
                Player p = Bukkit.getPlayer(arena.getPlayerUuid());
                if (p == null || !p.isOnline() || p.isDead()) return;
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);
                p.getWorld().strikeLightningEffect(p.getLocation());
                double newHp = Math.max(0.5, p.getHealth() - explosionDmg);
                p.setHealth(newHp);
                p.sendMessage("§c💥 §7Explosion! §c-" + String.format("%.1f", explosionDmg) + " ❤");
            }, 160L, 160L);
            arena.setExplosivTask(explosivTask);
        }

        // Periodisch Re-Target (alle 3 s), da EntityDamageEvent gecancelt wird
        if (arena.getTargetTask() != null) arena.getTargetTask().cancel();
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(arena.getPlayerUuid());
            if (p == null || !p.isOnline() || !entity.isValid()) return;
            if (entity instanceof Mob mob) {
                if (mob.getTarget() == null || !mob.getTarget().equals(p)) mob.setTarget(p);
            }
        }, 20L, 60L);
        arena.setTargetTask(task);
    }

    private EntityType getBossEntityType(int level) {
        if (level <  10) return EntityType.ZOMBIE;
        if (level <  25) return EntityType.SKELETON;
        if (level <  50) return EntityType.CAVE_SPIDER;
        if (level <  75) return EntityType.RAVAGER;
        if (level < 100) return EntityType.VINDICATOR;
        if (level < 150) return EntityType.PILLAGER;
        if (level < 200) return EntityType.RAVAGER;
        if (level < 300) return EntityType.VINDICATOR;
        if (level < 500) return EntityType.EVOKER;
        if (level < 750) return EntityType.IRON_GOLEM;
        return EntityType.WARDEN;
    }

    private void onBossDefeated(Player player, ArenaInstance arena) {
        if (arena.getBossEntity() != null && arena.getBossEntity().isValid()) arena.getBossEntity().remove();
        if (arena.getRegenTask() != null) { arena.getRegenTask().cancel(); arena.setRegenTask(null); }

        // Spieler auf volle Leben heilen + negative Effekte entfernen
        var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) player.setHealth(hpAttr.getValue());
        Set<PotionEffectType> negativeEffects = Set.of(
            PotionEffectType.POISON, PotionEffectType.WITHER,
            PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
            PotionEffectType.HUNGER, PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS, PotionEffectType.MINING_FATIGUE,
            PotionEffectType.LEVITATION, PotionEffectType.UNLUCK,
            PotionEffectType.DARKNESS
        );
        player.getActivePotionEffects().stream()
            .map(PotionEffect::getType)
            .filter(negativeEffects::contains)
            .forEach(player::removePotionEffect);

        UUID uuid         = player.getUniqueId();
        int defeatedLevel = arena.getBossLevel();
        int nextLevel     = Math.min(defeatedLevel + 1, 999);

        // Loot + Stats
        plugin.getLootManager().distributeSinglePlayer(player, defeatedLevel);
        plugin.getPlayerDataManager().addKillAndDamage(uuid, (long) arena.getSessionDamage(), defeatedLevel);
        plugin.getPlayerDataManager().setPersonalBossLevel(uuid, nextLevel);

        // Coins (+ luck rune multiplier)
        long baseCoins = defeatedLevel * 5L + 10L;
        long coins     = Math.round(baseCoins
            * plugin.getAbilityManager().getCoinMultiplier(uuid)
            * plugin.getRuneManager().getLuckRuneMultiplier(uuid));
        plugin.getCoinManager().addCoins(uuid, coins);
        player.sendMessage("§e✦ §6+" + coins + " §eMünzen");

        // Heal on Kill
        double healPct = plugin.getAbilityManager().getHealOnKillPercent(uuid);
        if (healPct > 0) {
            var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (hpAttr != null) {
                double healed = Math.min(player.getHealth() + hpAttr.getValue() * healPct, hpAttr.getValue());
                player.setHealth(healed);
            }
        }

        // Streak
        int newStreak = plugin.getStreakManager().incrementStreak(uuid);
        if (newStreak >= 2) {
            int bonusPct = (int) ((plugin.getStreakManager().getStreakMultiplier(uuid) - 1.0) * 100);
            player.sendMessage("§6🔥 Streak: §e" + newStreak + "x §8(+" + bonusPct + "% Schaden)");
        }

        // Milestone check
        plugin.getMilestoneManager().checkAndReward(player, nextLevel, plugin);

        // Rune charges decrement
        plugin.getRuneManager().onBossDefeated(uuid);

        // Forge charges decrement
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getForgeManager().onBossDefeated(uuid));

        // Bestiary tracking (entity type name)
        String mobType = getBossEntityType(defeatedLevel).name();
        plugin.getBestiaryManager().trackKillAndCheckFirst(uuid, mobType);

        // Weekly tournament
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getWeeklyManager().addKill(uuid, defeatedLevel));

        // Bounty check
        if (plugin.getBountyManager().tryClaimBounty(uuid, defeatedLevel)) {
            player.sendMessage("§6✦ §eTages-Kopfgeld eingelöst! §a+1000 Münzen §7& §d+3 Boss-Kerne§7!");
        }

        // 10-level milestone reward
        if (nextLevel % 10 == 0 && nextLevel > defeatedLevel) {
            long bonus = nextLevel * 10L;
            plugin.getCoinManager().addCoins(uuid, bonus);
            player.sendMessage("§d§l✦ §7Level-Meilenstein §d" + nextLevel + "§7! §6+" + bonus + " §eMünzen");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }

        // 10% Chance auf zufällige Verzauberung
        tryEnchantReward(player);

        // Combo increment
        plugin.getComboManager().increment(uuid);

        // Daily challenge tracking
        plugin.getDailyChallengeManager().addProgress(uuid, DailyChallengeManager.ChallengeType.BOSS_KILLS, 1);
        plugin.getDailyChallengeManager().addProgress(uuid, DailyChallengeManager.ChallengeType.TOTAL_DAMAGE,
            (int) Math.min(arena.getSessionDamage(), Integer.MAX_VALUE));
        plugin.getDailyChallengeManager().addProgress(uuid, DailyChallengeManager.ChallengeType.COINS_EARNED,
            (int) Math.min(coins, Integer.MAX_VALUE));
        if (newStreak >= 3) {
            plugin.getDailyChallengeManager().addProgress(uuid, DailyChallengeManager.ChallengeType.STREAK_REACH, newStreak);
        }

        // Titel
        player.showTitle(Title.title(
            Component.text("BOSS BESIEGT!", TextColor.color(0xFF5555), TextDecoration.BOLD),
            Component.text("Level " + defeatedLevel + " → " + nextLevel, NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        player.sendMessage("§c§l⚡ §7Boss Level §c" + defeatedLevel + " §7besiegt! Nächster: §cLevel " + nextLevel);
        player.sendMessage("§a▶ §7Rechtsklick §8» §7Boss-Ruf-Kristall §7um den nächsten Boss zu starten!");

        spawnFireworks(player.getLocation());

        // Boss-Bar auf Warte-Status setzen
        if (arena.getBossBar() != null) {
            arena.getBossBar().setTitle("§a✔ §7Boss besiegt!  §8|  §7Nächster: §cLevel " + nextLevel + "  §8– §7Kristall benutzen");
            arena.getBossBar().setProgress(1.0);
            arena.getBossBar().setColor(BarColor.BLUE);
        }

        // Warten bis Spieler bereit: Summon-Item geben statt Auto-Spawn
        arena.resetForNextBoss(nextLevel);
        arena.setNextBossLevel(nextLevel);
        arena.setBossReadyToSpawn(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arenas.containsKey(uuid) && player.isOnline()) {
                giveSummonItem(player, nextLevel);
                plugin.getScoreboardManager().update(player);
            }
        }, 60L);
    }

    /** Prüft Boss-Phasen (75% / 50% / 25%) und löst Spezial-Angriffe aus */
    private void checkBossPhases(Player player, ArenaInstance arena) {
        double pct = arena.getHpPercent();

        // Phase 1: 75% – Warnung + Sound
        if (pct <= 0.75 && !arena.isPhaseTriggered(0.75)) {
            arena.triggerPhase(0.75);
            player.sendMessage("§e⚡ §7Der Boss wird wütend! §e75% HP");
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.7f);
        }

        // Phase 2: 50% – Rage-Modus (30 Sek) + Blitz am Boss
        if (pct <= 0.50 && !arena.isPhaseTriggered(0.50)) {
            arena.triggerPhase(0.50);
            arena.activateRage(30_000L);
            player.sendMessage("§c§l☠ RAGE! §7Der Boss rast! §c+50% Schaden §7für 30s!");
            player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.5f);
            if (arena.getBossEntity() != null && arena.getWorld() != null)
                arena.getWorld().strikeLightningEffect(arena.getBossEntity().getLocation());
        }

        // Phase 3: 25% – Boss heilt 10% + Meteorregen
        if (pct <= 0.25 && !arena.isPhaseTriggered(0.25)) {
            arena.triggerPhase(0.25);
            double healAmt = arena.getConfig().maxHp() * 0.10;
            arena.heal(healAmt);
            updateBossBar(arena);
            player.sendMessage("§c☠ §7Der Boss heilt sich! §c+10% HP §7– Meteorregen!");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.2f);
            spawnMeteorShower(arena);
        }
    }

    private void spawnMeteorShower(ArenaInstance arena) {
        if (arena.getBossEntity() == null || arena.getWorld() == null) return;
        Location bossLoc = arena.getBossEntity().getLocation();
        World    world   = arena.getWorld();
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (arena.getBossEntity() == null || !arena.getBossEntity().isValid()) return;
                double angle  = idx * (Math.PI / 3.0);
                double dist   = 3 + idx % 3 * 2.0;
                Location strike = bossLoc.clone().add(
                    Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                world.strikeLightningEffect(strike);
            }, idx * 8L);
        }
    }

    private void updateBossBar(ArenaInstance arena) {
        BossBar bar = arena.getBossBar();
        if (bar == null) return;
        double pct = arena.getHpPercent();
        bar.setProgress(Math.max(0, Math.min(1, pct)));
        bar.setColor(pct > 0.5 ? BarColor.GREEN : pct > 0.25 ? BarColor.YELLOW : BarColor.RED);
        bar.setTitle(buildBarTitle(arena.getConfig(), arena.getCurrentHp(), arena.getModifiers()));
    }

    private String buildBarTitle(BossConfig cfg, double hp, Set<BossModifierManager.BossModifier> mods) {
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.displayName())
          .append(" §8– §f").append(cfg.formatHp(hp))
          .append(" §8/ §f").append(cfg.formatHp(cfg.maxHp()));
        if (mods != null && !mods.isEmpty()) {
            sb.append("  §8|");
            for (BossModifierManager.BossModifier mod : mods) {
                sb.append("  ").append(mod.icon).append("§7").append(mod.name);
            }
        }
        return sb.toString();
    }

    // ── Welt-Verwaltung ────────────────────────────────────────────────────

    private void copyTemplateWorld(String targetName) throws IOException {
        File template = new File(Bukkit.getWorldContainer(), "world");
        File target   = new File(Bukkit.getWorldContainer(), targetName);
        if (target.exists()) deleteDirectory(target.toPath());
        copyDirectory(template.toPath(), target.toPath());
        new File(target, "uid.dat").delete();
        new File(target, "session.lock").delete();
    }

    private void copyDirectory(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dst.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteWorldAsync(String worldName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File dir = new File(Bukkit.getWorldContainer(), worldName);
            if (dir.exists()) {
                try { deleteDirectory(dir.toPath()); }
                catch (IOException e) {
                    plugin.getLogger().warning("Arena-Welt nicht gelöscht: " + worldName);
                }
            }
        });
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!path.toFile().exists()) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void cleanupOrphanedArenas() {
        File worldContainer = Bukkit.getWorldContainer();
        File[] orphans = worldContainer.listFiles(
            f -> f.isDirectory() && f.getName().startsWith("arena_"));
        if (orphans == null || orphans.length == 0) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (File orphan : orphans) {
                try {
                    deleteDirectory(orphan.toPath());
                    plugin.getLogger().info("Verwaiste Arena gelöscht: " + orphan.getName());
                } catch (IOException e) {
                    plugin.getLogger().warning("Verwaiste Arena nicht löschbar: " + orphan.getName());
                }
            }
        });
    }

    private void cleanupArenaResources(ArenaInstance arena) {
        if (arena.getTargetTask()   != null) arena.getTargetTask().cancel();
        if (arena.getRegenTask()    != null) { arena.getRegenTask().cancel();    arena.setRegenTask(null); }
        if (arena.getExplosivTask() != null) { arena.getExplosivTask().cancel(); arena.setExplosivTask(null); }
        if (arena.getBossEntity() != null && arena.getBossEntity().isValid()) arena.getBossEntity().remove();
        if (arena.getBossBar()   != null) arena.getBossBar().removeAll();
        World world = arena.getWorld();
        if (world != null) {
            String name = world.getName();
            boolean unloaded = Bukkit.unloadWorld(world, false);
            if (!unloaded) {
                plugin.getLogger().warning("Arena-Welt konnte nicht entladen werden: " + name + " (noch Spieler drin?)");
            }
            deleteWorldAsync(name);
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private Location getPlayerSpawn(World world) {
        double x     = plugin.getConfig().getDouble("arena.spawn.x",   0.5);
        double y     = plugin.getConfig().getDouble("arena.spawn.y",  64.0);
        double z     = plugin.getConfig().getDouble("arena.spawn.z",   0.5);
        float  yaw   = (float) plugin.getConfig().getDouble("arena.spawn.yaw",   0.0);
        float  pitch = (float) plugin.getConfig().getDouble("arena.spawn.pitch",  0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location getBossSpawn(World world) {
        double x = plugin.getConfig().getDouble("arena.boss.x",   0.0);
        double y = plugin.getConfig().getDouble("arena.boss.y",  64.0);
        double z = plugin.getConfig().getDouble("arena.boss.z",   0.0);
        return new Location(world, x, y, z);
    }

    /** Teleportiert den Spieler in den Hub (Hauptwelt des Smash-Servers). */
    private void teleportToHub(Player player) {
        World hub = Bukkit.getWorlds().get(0);  // "world" = Hub des Smash-Servers
        player.teleport(getHubSpawn(hub));
        player.getInventory().clear();
    }

    private Location getHubSpawn(World world) {
        double x     = plugin.getConfig().getDouble("hub.spawn.x",   world.getSpawnLocation().getX());
        double y     = plugin.getConfig().getDouble("hub.spawn.y",   world.getSpawnLocation().getY());
        double z     = plugin.getConfig().getDouble("hub.spawn.z",   world.getSpawnLocation().getZ());
        float  yaw   = (float) plugin.getConfig().getDouble("hub.spawn.yaw",   0.0);
        float  pitch = (float) plugin.getConfig().getDouble("hub.spawn.pitch",  0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void giveArenaItems(Player player) {
        player.getInventory().clear();
        LegacyComponentSerializer leg = LegacyComponentSerializer.legacySection();

        // Slot 0 – Schwert
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta  sm    = sword.getItemMeta();
        sm.displayName(leg.deserialize("§c§lBoss-Schwert"));
        sm.lore(java.util.List.of(leg.deserialize("§7Schaden durch Upgrades & Fähigkeiten")));
        sm.setUnbreakable(true);
        sm.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        sword.setItemMeta(sm);
        player.getInventory().setItem(0, sword);

        // Slot 1 – Bogen (Infinity + Power V)
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta  bm  = bow.getItemMeta();
        bm.displayName(leg.deserialize("§a§lBoss-Bogen"));
        bm.lore(java.util.List.of(
            leg.deserialize("§7Alternativwaffe – auch durch"),
            leg.deserialize("§7Upgrades & Fähigkeiten verstärkt")));
        bm.setUnbreakable(true);
        bm.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        bow.setItemMeta(bm);
        bow.addUnsafeEnchantment(Enchantment.INFINITY, 1);
        bow.addUnsafeEnchantment(Enchantment.POWER, 5);
        player.getInventory().setItem(1, bow);

        // Slot 2 – 1 Pfeil (für Infinity benötigt)
        player.getInventory().setItem(2, new ItemStack(Material.ARROW, 1));

        // Slot 8 – Navigator-Kompass (ganz rechts)
        player.getInventory().setItem(8, SmashNavigatorListener.buildCompass());

        // Rüstung
        player.getInventory().setItem(36, makeArmor(Material.DIAMOND_BOOTS,      "§7Boots"));
        player.getInventory().setItem(37, makeArmor(Material.DIAMOND_LEGGINGS,   "§7Leggings"));
        player.getInventory().setItem(38, makeArmor(Material.DIAMOND_CHESTPLATE, "§7Chestplate"));
        player.getInventory().setItem(39, makeArmor(Material.DIAMOND_HELMET,     "§7Helm"));

        // Schild (Offhand)
        ItemStack shield = new ItemStack(Material.SHIELD);
        ItemMeta  shm    = shield.getItemMeta();
        shm.displayName(leg.deserialize("§7§lSchild"));
        shm.setUnbreakable(true);
        shm.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        shield.setItemMeta(shm);
        player.getInventory().setItem(40, shield);

        plugin.getUpgradeManager().applyStats(player);
    }

    private ItemStack makeArmor(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    // ── Summon-Item ────────────────────────────────────────────────────────

    public static final String SUMMON_ITEM_NAME = "Boss-Ruf-Kristall";

    private void giveSummonItem(Player player, int nextLevel) {
        LegacyComponentSerializer leg = LegacyComponentSerializer.legacySection();
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(leg.deserialize("§a§l▶ " + SUMMON_ITEM_NAME));
        meta.lore(java.util.List.of(
            leg.deserialize("§7Nächster Boss: §cLevel " + nextLevel),
            leg.deserialize("§8─────────────────────"),
            leg.deserialize("§7Rechtsklick §8» §aNeuen Boss starten")));
        item.setItemMeta(meta);
        player.getInventory().setItem(3, item);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
    }

    /** Wird aufgerufen wenn Spieler den Boss-Ruf-Kristall rechtsklickt */
    public void triggerBossSpawn(Player player) {
        ArenaInstance arena = arenas.get(player.getUniqueId());
        if (arena == null || !arena.isBossReadyToSpawn()) return;
        arena.setBossReadyToSpawn(false);

        // Summon-Item entfernen
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack it = player.getInventory().getItem(i);
            if (it != null && it.getType() == Material.NETHER_STAR && it.hasItemMeta()) {
                Component name = it.getItemMeta().displayName();
                if (name != null && LegacyComponentSerializer.legacySection().serialize(name).contains(SUMMON_ITEM_NAME)) {
                    player.getInventory().setItem(i, null);
                    break;
                }
            }
        }

        spawnBossInArena(arena);
        plugin.getScoreboardManager().update(player);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
    }

    private void tryEnchantReward(Player player) {
        if (Math.random() > 0.10) return;

        // Slot → mögliche Verzauberungen (Level-Range: min, max)
        record EnchOption(Enchantment ench, int minLvl, int maxLvl) {}
        Map<Integer, List<EnchOption>> slotEnchants = new HashMap<>();
        slotEnchants.put(0,  List.of(                                          // Schwert
            new EnchOption(Enchantment.KNOCKBACK,   1, 2),
            new EnchOption(Enchantment.FIRE_ASPECT, 1, 2),
            new EnchOption(Enchantment.SHARPNESS,   1, 5)
        ));
        slotEnchants.put(1,  List.of(                                          // Bogen
            new EnchOption(Enchantment.FLAME, 1, 1),
            new EnchOption(Enchantment.PUNCH, 1, 2),
            new EnchOption(Enchantment.POWER, 1, 5)
        ));
        slotEnchants.put(39, List.of(                                          // Helm
            new EnchOption(Enchantment.PROTECTION,   1, 4),
            new EnchOption(Enchantment.THORNS,       1, 3),
            new EnchOption(Enchantment.RESPIRATION,  1, 3)
        ));
        slotEnchants.put(38, List.of(                                          // Chestplate
            new EnchOption(Enchantment.PROTECTION, 1, 4),
            new EnchOption(Enchantment.THORNS,     1, 3)
        ));
        slotEnchants.put(37, List.of(                                          // Leggings
            new EnchOption(Enchantment.PROTECTION, 1, 4),
            new EnchOption(Enchantment.THORNS,     1, 3)
        ));
        slotEnchants.put(36, List.of(                                          // Boots
            new EnchOption(Enchantment.PROTECTION,    1, 4),
            new EnchOption(Enchantment.FEATHER_FALLING, 1, 4),
            new EnchOption(Enchantment.THORNS,        1, 3)
        ));

        List<Integer> slots = new ArrayList<>(slotEnchants.keySet());
        Collections.shuffle(slots);
        int count = 1 + (int) (Math.random() * 3); // 1–3 Items

        List<String> enchantedNames = new ArrayList<>();
        for (int slot : slots) {
            if (enchantedNames.size() >= count) break;
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            List<EnchOption> options = slotEnchants.get(slot);
            EnchOption chosen = options.get((int) (Math.random() * options.size()));
            int level = chosen.minLvl() + (int) (Math.random() * (chosen.maxLvl() - chosen.minLvl() + 1));

            item.addUnsafeEnchantment(chosen.ench(), level);
            player.getInventory().setItem(slot, item);

            String slotName = switch (slot) {
                case 0  -> "Schwert";
                case 1  -> "Bogen";
                case 39 -> "Helm";
                case 38 -> "Chestplate";
                case 37 -> "Leggings";
                case 36 -> "Boots";
                default -> "Item";
            };
            enchantedNames.add("§f" + slotName + " §8(§d" + chosen.ench().getKey().getKey() + " " + level + "§8)");
        }

        if (!enchantedNames.isEmpty()) {
            player.sendMessage("§d✦ §7Verzauberungsbonus! " + String.join("§7, ", enchantedNames));
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        }
    }

    private void spawnFireworks(Location loc) {
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
                var meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.RED, Color.YELLOW)
                    .withFade(Color.WHITE)
                    .with(FireworkEffect.Type.STAR)
                    .trail(true).build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);
            }, i * 10L);
        }
    }
}
