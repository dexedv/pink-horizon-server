package de.pinkhorizon.smash.arena;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.boss.BossConfig;
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

        cleanupArenaResources(arena);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            teleportToHub(player);
        }
    }

    /** Schaden auf Boss des Spielers anwenden (vom CombatListener aufgerufen) */
    public void applyDamage(Player player, double rawDamage) {
        ArenaInstance arena = arenas.get(player.getUniqueId());
        if (arena == null) return;

        double multiplier = plugin.getUpgradeManager().getAttackMultiplier(player.getUniqueId());
        double real       = rawDamage * multiplier;
        arena.applyDamage(real);

        // Lifesteal
        double lifesteal = plugin.getUpgradeManager().getLifestealPercent(player.getUniqueId());
        if (lifesteal > 0) {
            var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (hpAttr != null) {
                double healed = Math.min(player.getHealth() + real * lifesteal, hpAttr.getValue());
                player.setHealth(healed);
            }
        }

        updateBossBar(arena);
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
            if (arena.getTargetTask() != null) arena.getTargetTask().cancel();
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

        BossBar bar = Bukkit.createBossBar(
            buildBarTitle(cfg, cfg.maxHp()), BarColor.GREEN, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);

        Player owner = Bukkit.getPlayer(arena.getPlayerUuid());
        if (owner != null) {
            bar.addPlayer(owner);
            if (entity instanceof Mob mob) mob.setTarget(owner);
        }

        arena.setBossEntity(entity);
        arena.setBossBar(bar);

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
        if (arena.getBossBar()   != null) arena.getBossBar().removeAll();

        int defeatedLevel = arena.getBossLevel();
        int nextLevel     = Math.min(defeatedLevel + 1, 999);

        // Loot + Stats
        plugin.getLootManager().distributeSinglePlayer(player, defeatedLevel);
        plugin.getPlayerDataManager().addKillAndDamage(player.getUniqueId(),
            (long) arena.getSessionDamage(), defeatedLevel);
        plugin.getPlayerDataManager().setPersonalBossLevel(player.getUniqueId(), nextLevel);

        // Coins
        long baseCoins = defeatedLevel * 5L + 10L;
        long coins     = Math.round(baseCoins * plugin.getAbilityManager().getCoinMultiplier(player.getUniqueId()));
        plugin.getCoinManager().addCoins(player.getUniqueId(), coins);
        player.sendMessage("§e✦ §6+" + coins + " §eMünzen");

        // Heal on Kill
        double healPct = plugin.getAbilityManager().getHealOnKillPercent(player.getUniqueId());
        if (healPct > 0) {
            var hpAttr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (hpAttr != null) {
                double healed = Math.min(player.getHealth() + hpAttr.getValue() * healPct, hpAttr.getValue());
                player.setHealth(healed);
            }
        }

        // Titel
        player.showTitle(Title.title(
            Component.text("BOSS BESIEGT!", TextColor.color(0xFF5555), TextDecoration.BOLD),
            Component.text("Level " + defeatedLevel + " → " + nextLevel, NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        player.sendMessage("§c§l⚡ §7Boss Level §c" + defeatedLevel + " §7besiegt! Nächster: §cLevel " + nextLevel);

        spawnFireworks(player.getLocation());

        // Nächsten Boss nach 3 Sekunden spawnen
        arena.resetForNextBoss(nextLevel);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arenas.containsKey(player.getUniqueId()) && player.isOnline()) {
                spawnBossInArena(arena);
                plugin.getScoreboardManager().update(player);
            }
        }, 60L);
    }

    private void updateBossBar(ArenaInstance arena) {
        BossBar bar = arena.getBossBar();
        if (bar == null) return;
        double pct = arena.getHpPercent();
        bar.setProgress(Math.max(0, Math.min(1, pct)));
        bar.setColor(pct > 0.5 ? BarColor.GREEN : pct > 0.25 ? BarColor.YELLOW : BarColor.RED);
        bar.setTitle(buildBarTitle(arena.getConfig(), arena.getCurrentHp()));
    }

    private String buildBarTitle(BossConfig cfg, double hp) {
        return cfg.displayName() + " §8– §f" + cfg.formatHp(hp) + " §8/ §f" + cfg.formatHp(cfg.maxHp());
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
        if (arena.getTargetTask() != null) arena.getTargetTask().cancel();
        if (arena.getBossEntity() != null && arena.getBossEntity().isValid()) arena.getBossEntity().remove();
        if (arena.getBossBar()   != null) arena.getBossBar().removeAll();
        World world = arena.getWorld();
        if (world != null) {
            String name = world.getName();
            Bukkit.unloadWorld(world, false);
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
