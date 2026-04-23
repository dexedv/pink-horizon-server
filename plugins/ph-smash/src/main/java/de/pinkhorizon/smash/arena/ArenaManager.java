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
import org.bukkit.entity.Player;

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
                world.setTime(6000);

                arena.setWorld(world);
                spawnBossInArena(arena);
                player.teleport(getPlayerSpawn(world));
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
            sendToLobby(player);
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

    /** Beim Plugin-Disable: alle Arenen synchron aufräumen */
    public void destroyAll() {
        for (ArenaInstance arena : new ArrayList<>(arenas.values())) {
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

        EntityType type = cfg.level() >= 100 ? EntityType.WARDEN : EntityType.IRON_GOLEM;
        LivingEntity entity = (LivingEntity) world.spawnEntity(loc, type);

        entity.setCustomName(cfg.displayName());
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setGlowing(true);

        var hpAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(99999);
            entity.setHealth(99999);
        }

        BossBar bar = Bukkit.createBossBar(
            buildBarTitle(cfg, cfg.maxHp()), BarColor.GREEN, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);

        Player owner = Bukkit.getPlayer(arena.getPlayerUuid());
        if (owner != null) bar.addPlayer(owner);

        arena.setBossEntity(entity);
        arena.setBossBar(bar);
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

    private void sendToLobby(Player player) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(b)) {
            out.writeUTF("Connect");
            out.writeUTF("lobby");
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (IOException e) {
            // Fallback: Spawn der Hauptwelt
            World main = Bukkit.getWorlds().get(0);
            player.teleport(main.getSpawnLocation());
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
