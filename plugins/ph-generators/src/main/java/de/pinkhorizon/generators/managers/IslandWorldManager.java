package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Verwaltet pro-Spieler-Inseln basierend auf dem island-template.
 *
 * <p>Ablauf:
 * <ul>
 *   <li>Erster Join → Template nach island_&lt;UUID&gt;/ kopieren → Welt laden → teleportieren</li>
 *   <li>Wiederkehrender Spieler → Welt laden → teleportieren</li>
 *   <li>Logout → Welt entladen (Dateien bleiben erhalten)</li>
 * </ul>
 *
 * <p>Welten liegen als island_&lt;UUID&gt;/ direkt im Server-Root (Bukkit.getWorldContainer()).
 * Neue Chunks außerhalb der Insel werden vom VoidChunkGenerator leer generiert.
 */
public class IslandWorldManager {

    private static final String WORLD_PREFIX = "island_";
    private final PHGenerators plugin;
    private final Logger log;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public IslandWorldManager(PHGenerators plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    // ── Öffentliche API ──────────────────────────────────────────────────────

    /**
     * Lädt oder erstellt die Insel-Welt des Spielers und teleportiert ihn dorthin.
     * Wird asynchron aufgerufen, die Teleportation selbst synchron.
     */
    public void loadAndTeleport(Player player) {
        UUID uuid = player.getUniqueId();
        String worldName = WORLD_PREFIX + uuid;

        // Synchron: Welt bereits geladen?
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            teleport(player, existing);
            return;
        }

        // Welt-Ordner existiert → nur laden, sonst Template kopieren (oder leer anlegen)
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (!worldFolder.exists()) {
            File template = new File(Bukkit.getWorldContainer(), "island-template");
            boolean hasTemplate = template.exists() && template.isDirectory()
                    && template.list() != null && template.list().length > 0;

            if (hasTemplate) {
                log.info("[IslandWorld] Erstelle neue Insel aus Template für " + player.getName());
                if (!copyTemplate(worldFolder)) {
                    player.sendMessage(MM.deserialize(
                            "<red>Fehler beim Erstellen deiner Insel! Bitte Admin kontaktieren."));
                    return;
                }
            } else {
                // Kein Template → leere Welt anlegen (VoidChunkGenerator + Glasplattform)
                log.info("[IslandWorld] Erstelle neue leere Insel für " + player.getName());
                worldFolder.mkdirs();
            }
        }

        // Welt asynchron vorbereiten, dann synchron laden (Bukkit-Anforderung)
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = loadWorld(worldName);
            if (world == null) {
                player.sendMessage(MM.deserialize(
                        "<red>Deine Insel konnte nicht geladen werden! Bitte Admin kontaktieren."));
                return;
            }
            teleport(player, world);
        });
    }

    /**
     * Entlädt die Insel-Welt des Spielers (speichert dabei alle Daten).
     * Muss synchron (Hauptthread) aufgerufen werden.
     */
    public void unloadIsland(UUID uuid) {
        String worldName = WORLD_PREFIX + uuid;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // Spawn-Info-Hologramm entfernen
        plugin.getHologramManager().removeSpawnHolo(uuid);

        // Alle Spieler aus der Welt entfernen (Sicherheit)
        Location lobby = getFallbackLocation();
        for (Player p : world.getPlayers()) {
            p.teleport(lobby);
        }

        boolean unloaded = Bukkit.unloadWorld(world, true);
        if (unloaded) {
            log.info("[IslandWorld] Insel entladen: " + worldName);
        } else {
            log.warning("[IslandWorld] Konnte Insel nicht entladen: " + worldName);
        }
    }

    /**
     * Gibt den Welt-Namen der Spieler-Insel zurück (unabhängig davon ob geladen).
     */
    public String getWorldName(UUID uuid) {
        return WORLD_PREFIX + uuid;
    }

    /**
     * Prüft ob eine Welt die Insel eines bestimmten Spielers ist.
     */
    public boolean isOwnIsland(World world, UUID uuid) {
        return world.getName().equals(WORLD_PREFIX + uuid);
    }

    /**
     * Prüft ob eine Welt überhaupt eine Spieler-Insel ist.
     */
    public boolean isIslandWorld(World world) {
        return world.getName().startsWith(WORLD_PREFIX);
    }

    /**
     * Gibt die UUID des Besitzers einer Insel-Welt zurück, oder null.
     */
    public UUID getOwner(World world) {
        if (!isIslandWorld(world)) return null;
        try {
            return UUID.fromString(world.getName().substring(WORLD_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Welt laden ───────────────────────────────────────────────────────────

    private World loadWorld(String worldName) {
        try {
            WorldCreator creator = new WorldCreator(worldName)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false)
                    .generator(new VoidChunkGenerator());
            World world = creator.createWorld();
            if (world != null) {
                world.setAutoSave(true);
                world.setDifficulty(Difficulty.PEACEFUL);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setGameRule(GameRule.MOB_GRIEFING, false);
                world.setGameRule(GameRule.KEEP_INVENTORY, true);
                world.setTime(6000); // Mittag
                world.setSpawnFlags(false, false);
                log.info("[IslandWorld] Welt geladen: " + worldName);
            }
            return world;
        } catch (Exception e) {
            log.severe("[IslandWorld] Fehler beim Laden von " + worldName + ": " + e.getMessage());
            return null;
        }
    }

    // ── Teleportation ─────────────────────────────────────────────────────────

    private void teleport(Player player, World world) {
        double spawnX = plugin.getConfig().getDouble("island.spawn-x", 0.5);
        double spawnY = plugin.getConfig().getDouble("island.spawn-y", 64.0);
        double spawnZ = plugin.getConfig().getDouble("island.spawn-z", 0.5);
        float yaw   = (float) plugin.getConfig().getDouble("island.spawn-yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble("island.spawn-pitch", 0.0);

        Location spawn = new Location(world, spawnX, spawnY, spawnZ, yaw, pitch);

        // Sicherstellen dass der Chunk geladen ist
        world.loadChunk(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4, true);

        // Plattform-Fallback: falls kein Boden vorhanden, Glass-Plattform setzen
        org.bukkit.block.Block below = world.getBlockAt((int) spawnX, (int) spawnY - 1, (int) spawnZ);
        if (below.getType() == org.bukkit.Material.AIR
                || below.getType() == org.bukkit.Material.VOID_AIR
                || below.getType() == org.bukkit.Material.CAVE_AIR) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.getBlockAt((int) spawnX + dx, (int) spawnY - 1, (int) spawnZ + dz)
                            .setType(org.bukkit.Material.GLASS);
                }
            }
            log.info("[IslandWorld] Spawn-Plattform erstellt für " + player.getName());
        }

        player.teleport(spawn);
        player.setGameMode(GameMode.SURVIVAL);

        // World-Border setzen
        de.pinkhorizon.generators.data.PlayerData borderData =
                plugin.getPlayerDataMap().get(player.getUniqueId());
        if (borderData != null) {
            plugin.getWorldBorderManager().applyBorder(world, borderData);
        }

        // Welt ist jetzt sicher geladen → Blöcke wiederherstellen + Hologramme spawnen
        de.pinkhorizon.generators.data.PlayerData data =
                plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data != null) {
            final Location spawnLoc = new Location(world, spawnX, spawnY, spawnZ);
            // 3 Ticks warten damit der Client die Welt fertig empfangen hat
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getGeneratorManager().loadHolograms(data);
                // Spawn-Info-Hologramm 4 Blöcke neben dem Spawnpunkt setzen
                plugin.getHologramManager().setSpawnHolo(player.getUniqueId(), spawnLoc);
            }, 3L);
        }

    }

    // ── Template kopieren ─────────────────────────────────────────────────────

    private boolean copyTemplate(File dest) {
        File template = new File(Bukkit.getWorldContainer(), "island-template");
        if (!template.exists() || !template.isDirectory()) {
            log.severe("[IslandWorld] island-template Ordner nicht gefunden! Pfad: " + template.getAbsolutePath());
            return false;
        }
        try {
            copyDirectory(template.toPath(), dest.toPath());
            // Session-Lock entfernen falls vorhanden
            new File(dest, "session.lock").delete();
            log.info("[IslandWorld] Template kopiert nach " + dest.getName());
            return true;
        } catch (IOException e) {
            log.severe("[IslandWorld] Template-Kopierfehler: " + e.getMessage());
            return false;
        }
    }

    private void copyDirectory(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Spielerdaten-Ordner überspringen
                String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (dirName.equals("players") || dirName.equals("playerdata")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(dest.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // level.dat überspringen → Paper generiert eine frische für die neue Welt
                String name = file.getFileName().toString();
                if (name.equals("level.dat") || name.equals("level.dat_old") || name.equals("session.lock")) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ── Fallback Location (Lobby oder Spawn-Welt) ────────────────────────────

    private Location getFallbackLocation() {
        World fallback = Bukkit.getWorlds().stream()
                .filter(w -> !isIslandWorld(w))
                .findFirst()
                .orElse(Bukkit.getWorlds().get(0));
        return fallback.getSpawnLocation();
    }
}
