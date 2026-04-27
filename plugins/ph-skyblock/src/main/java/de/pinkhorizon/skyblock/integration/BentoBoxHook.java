package de.pinkhorizon.skyblock.integration;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.database.objects.Island;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Wrapper für die BentoBox API.
 * Zentralisiert alle BentoBox-Zugriffe und behandelt graceful den Fall
 * dass BentoBox nicht geladen ist.
 */
public final class BentoBoxHook {

    private static BentoBox api;
    private static final Logger LOG = Logger.getLogger("PH-SkyBlock");

    private BentoBoxHook() {}

    public static void init() {
        if (Bukkit.getPluginManager().getPlugin("BentoBox") != null) {
            api = BentoBox.getInstance();
            LOG.info("[PH-SkyBlock] BentoBox-Integration aktiviert.");
        } else {
            LOG.warning("[PH-SkyBlock] BentoBox nicht gefunden – SkyBlock-Features eingeschränkt.");
        }
    }

    public static boolean isAvailable() {
        return api != null;
    }

    /** Gibt die BSkyBlock-Oberwelt zurück (die Welt mit den Inseln). */
    public static Optional<World> getSkyBlockWorld() {
        if (api == null) return Optional.empty();
        return api.getAddonsManager()
                .getAddonByName("BSkyBlock")
                .filter(a -> a instanceof GameModeAddon)
                .map(a -> ((GameModeAddon) a).getOverWorld());
    }

    /** Gibt die Insel des Spielers zurück (falls vorhanden). */
    public static Optional<Island> getIsland(UUID uuid) {
        if (api == null) return Optional.empty();
        return getSkyBlockWorld()
                .map(w -> api.getIslandsManager().getIsland(w, uuid));
    }

    /** Teleportiert den Spieler zu seinem Insel-Home. */
    public static void teleportHome(Player player) {
        if (api == null) {
            player.sendMessage("§cBentoBox ist nicht verfügbar.");
            return;
        }
        getSkyBlockWorld().ifPresentOrElse(
            w -> api.getIslandsManager().homeTeleportAsync(w, player),
            () -> player.sendMessage("§cSkyBlock-Welt nicht gefunden.")
        );
    }

    /** Gibt das Island-Level zurück (via Level-Addon falls vorhanden, sonst 0). */
    public static long getIslandLevel(UUID uuid) {
        if (api == null) return 0;
        // Level-Addon via Reflection (optionale Dependency)
        var levelPlugin = Bukkit.getPluginManager().getPlugin("Level");
        if (levelPlugin != null) {
            try {
                var world = getSkyBlockWorld().orElse(null);
                if (world == null) return 0;
                var method = levelPlugin.getClass().getMethod("getIslandLevel", World.class, UUID.class);
                Object result = method.invoke(levelPlugin, world, uuid);
                if (result instanceof Long l) return l;
                if (result instanceof Number n) return n.longValue();
            } catch (Exception ignored) {}
        }
        // Fallback: Level-Addon nicht verfügbar → 0
        return 0L;
    }

    /** Prüft ob der Spieler eine Insel hat. */
    public static boolean hasIsland(UUID uuid) {
        return getIsland(uuid).isPresent();
    }

    /** Gibt die Insel-Größe zurück (protection range × 2). */
    public static int getIslandSize(UUID uuid) {
        return getIsland(uuid)
                .map(i -> i.getProtectionRange() * 2)
                .orElse(0);
    }
}
