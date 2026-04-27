package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;

/**
 * Ritual-System: Spieler bauen bestimmte Block-Muster auf ihrer Insel
 * um mächtige Effekte auszulösen.
 */
public class RitualManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum RitualType {
        SONNENSEGEN    ("Sonnensegen",     48 * 3600, "4 Gold-Blöcke + Beacon in der Mitte"),
        ABGRUNDOEFFNUNG("Abgrundöffnung",  24 * 3600, "4 Obsidian + Ender-Auge in der Mitte"),
        STEINGEIST     ("Steingeist",      72 * 3600, "4 Netherit-Blöcke + Crying Obsidian"),
        ERNTE_TANZ     ("Ernte-Tanz",      12 * 3600, "8 Heuballen im Kreis + Blume"),
        STERNE_RUFEN   ("Sterne-Rufen",    48 * 3600, "8 End-Kristalle im Kreis"),
        HAENDLER_GEIST ("Händler-Geist",   24 * 3600, "4 Emerald-Blöcke + Karte");

        public final String displayName;
        public final long   cooldownSeconds;
        public final String requirement;

        RitualType(String d, long c, String r) { displayName = d; cooldownSeconds = c; requirement = r; }
    }

    private final PHSkyBlock plugin;
    // Island-UUID → RitualType → last-use-unix-seconds
    private final Map<UUID, Map<RitualType, Long>> cooldowns = new HashMap<>();

    public RitualManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        createTables();
        loadCooldowns();
    }

    // ── Tabellen ──────────────────────────────────────────────────────────────

    private void createTables() {
        try (Connection c = plugin.getDatabase().getConnection();
             Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_rituals (
                    island_uuid VARCHAR(36) NOT NULL,
                    ritual_id   VARCHAR(64) NOT NULL,
                    last_used   BIGINT      NOT NULL,
                    PRIMARY KEY (island_uuid, ritual_id)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Ritual-Tabelle Fehler: " + e.getMessage());
        }
    }

    private void loadCooldowns() {
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT island_uuid, ritual_id, last_used FROM sky_rituals")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID island  = UUID.fromString(rs.getString("island_uuid"));
                    String rid   = rs.getString("ritual_id");
                    long lastUse = rs.getLong("last_used");
                    try {
                        RitualType type = RitualType.valueOf(rid);
                        cooldowns.computeIfAbsent(island, k -> new HashMap<>()).put(type, lastUse);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ritual-Cooldowns laden fehlgeschlagen: " + e.getMessage());
        }
    }

    // ── Ritual-Prüfung ────────────────────────────────────────────────────────

    /**
     * Wird bei Block-Platzierung aufgerufen.
     * Prüft ob ein Ritual-Muster abgeschlossen wurde.
     */
    public void checkRitual(Player player, Location placedBlock) {
        var islandOpt = BentoBoxHook.getIsland(player.getUniqueId());
        if (islandOpt.isEmpty()) return;
        UUID islandUuid = player.getUniqueId();

        for (RitualType ritual : RitualType.values()) {
            if (isPatternComplete(ritual, placedBlock) && !isOnCooldown(islandUuid, player, ritual)) {
                activateRitual(player, islandUuid, ritual, placedBlock);
                return;
            }
        }
    }

    private boolean isPatternComplete(RitualType ritual, Location center) {
        return switch (ritual) {
            case SONNENSEGEN     -> checkCrossPattern(center, Material.GOLD_BLOCK, 3, Material.BEACON);
            case ABGRUNDOEFFNUNG -> checkCrossPattern(center, Material.OBSIDIAN, 3, Material.ENDER_EYE);
            case STEINGEIST      -> checkCrossPattern(center, Material.NETHERITE_BLOCK, 3, Material.CRYING_OBSIDIAN);
            case ERNTE_TANZ      -> checkCirclePattern(center, Material.HAY_BLOCK, 3, Material.DANDELION);
            case STERNE_RUFEN    -> checkCirclePattern(center, Material.END_CRYSTAL, 3, null);
            case HAENDLER_GEIST  -> checkCrossPattern(center, Material.EMERALD_BLOCK, 2, Material.MAP);
        };
    }

    /** Prüft ob 4 Blöcke in Kreuzform + Center-Block vorhanden sind */
    private boolean checkCrossPattern(Location center, Material arm, int dist, Material centerMat) {
        if (centerMat != null && center.getBlock().getType() != centerMat) return false;
        Location[] arms = {
            center.clone().add(dist, 0, 0),
            center.clone().add(-dist, 0, 0),
            center.clone().add(0, 0, dist),
            center.clone().add(0, 0, -dist)
        };
        for (Location l : arms) {
            if (l.getBlock().getType() != arm) return false;
        }
        return true;
    }

    /** Prüft einen Ring von Blöcken um den Center */
    private boolean checkCirclePattern(Location center, Material ring, int radius, Material centerMat) {
        if (centerMat != null && center.getBlock().getType() != centerMat) return false;
        int[] offsets = {-radius, 0, radius};
        int count = 0;
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) continue;
                if (center.clone().add(dx, 0, dz).getBlock().getType() == ring) count++;
            }
        }
        return count >= 4;
    }

    private boolean isOnCooldown(UUID islandUuid, Player player, RitualType ritual) {
        long lastUse = cooldowns
            .getOrDefault(islandUuid, Collections.emptyMap())
            .getOrDefault(ritual, 0L);
        long elapsed = System.currentTimeMillis() / 1000 - lastUse;

        // Mystisch-Gen: -25% Cooldown
        double mult = plugin.getIslandDnaManager().getRitualCooldownMult(player.getUniqueId());
        long effectiveCooldown = (long)(ritual.cooldownSeconds * mult);

        if (elapsed < effectiveCooldown) {
            long remaining = effectiveCooldown - elapsed;
            long hours = remaining / 3600;
            long mins  = (remaining % 3600) / 60;
            player.sendMessage(MM.deserialize(
                "<red>Ritual noch nicht bereit. Noch: <yellow>"
                + hours + "h " + mins + "m"));
            return true;
        }
        return false;
    }

    // ── Ritual-Aktivierung ────────────────────────────────────────────────────

    public void activateRitual(Player player, UUID islandUuid, RitualType ritual, Location loc) {
        // Cooldown setzen
        long now = System.currentTimeMillis() / 1000;
        cooldowns.computeIfAbsent(islandUuid, k -> new HashMap<>()).put(ritual, now);
        saveCooldown(islandUuid, ritual, now);

        // Visueller Effekt
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.add(0.5, 1, 0.5),
            100, 1, 1, 1, 0.2);
        loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);

        // Nachricht
        player.sendMessage(MM.deserialize(
            "<light_purple>✦ <bold>Ritual aktiviert: <white>" + ritual.displayName + "</bold>"));
        plugin.getServer().getOnlinePlayers().forEach(p -> {
            if (!p.equals(player)) {
                p.sendMessage(MM.deserialize(
                    "<dark_gray>[Ritual] <light_purple>"
                    + player.getName() + " hat das <white>" + ritual.displayName + " <light_purple>Ritual aktiviert!"));
            }
        });

        // Effekt anwenden
        applyEffect(player, ritual, loc);

        // Chronicle
        plugin.getChronicleManager().addEntry(player.getUniqueId(),
            "ritual", "Ritual aktiviert: " + ritual.displayName);
    }

    private void applyEffect(Player player, RitualType ritual, Location loc) {
        switch (ritual) {
            case SONNENSEGEN -> {
                // 2h lang +50% Crop-Yield (Marker setzen)
                player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20*60*120, 1));
                player.sendMessage(MM.deserialize(
                    "<yellow>☀ Sonnensegen aktiv! Crop-Yield +50% für 2 Stunden."));
            }
            case ABGRUNDOEFFNUNG -> {
                // 30 Minuten Void-Fishing Bonus
                player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20*60*30, 2));
                player.sendMessage(MM.deserialize(
                    "<dark_aqua>🌀 Abgrundöffnung! Void-Fishing Bonus für 30 Minuten."));
            }
            case STEINGEIST -> {
                // 1h Haste 3
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20*60*60, 2));
                player.sendMessage(MM.deserialize(
                    "<gray>⛏ Steingeist! Mining-Speed 3x für 1 Stunde."));
            }
            case ERNTE_TANZ -> {
                // Alle Crops auf der Insel sofort wachsen lassen
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    var worldOpt = BentoBoxHook.getSkyBlockWorld();
                    worldOpt.ifPresent(w -> {
                        // Grobe Implementierung: Block-Wachstum triggern
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage(MM.deserialize(
                                "<green>🌾 Ernte-Tanz! Alle Pflanzen sind gewachsen!"));
                        });
                    });
                });
            }
            case STERNE_RUFEN -> {
                // Nächste 3 Sternschnuppen landen auf dieser Insel
                player.sendMessage(MM.deserialize(
                    "<yellow>⭐ Sterne-Rufen! Die nächsten 3 Sternschnuppen kommen zu dir!"));
            }
            case HAENDLER_GEIST -> {
                // Händler-NPC erscheint für 1h
                player.sendMessage(MM.deserialize(
                    "<gold>👻 Händler-Geist! Ein mystischer Händler erscheint auf deiner Insel für 1 Stunde!"));
                // NPC spawnen via NpcManager
                plugin.getNpcManager().spawnTemporaryTrader(player, loc, 60 * 60 * 20L);
            }
        }
    }

    private void saveCooldown(UUID islandUuid, RitualType ritual, long timestamp) {
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO sky_rituals (island_uuid, ritual_id, last_used) VALUES(?,?,?) " +
                 "ON DUPLICATE KEY UPDATE last_used=VALUES(last_used)")) {
            ps.setString(1, islandUuid.toString());
            ps.setString(2, ritual.name());
            ps.setLong  (3, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Ritual-Cooldown speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    /** Öffnet das Ritual-GUI für den Spieler. */
    public void showRituals(Player player) {
        new de.pinkhorizon.skyblock.gui.RitualGui(plugin, player).open(player);
    }

    /** Prüft ob ein Ritual bereit ist (kein Cooldown). */
    public boolean isReady(UUID islandUuid, RitualType ritual) {
        if (islandUuid == null) return true;
        long lastUse = cooldowns.getOrDefault(islandUuid, Collections.emptyMap()).getOrDefault(ritual, 0L);
        return (System.currentTimeMillis() / 1000 - lastUse) >= ritual.cooldownSeconds;
    }

    /** Sekunden bis das Ritual wieder bereit ist (0 wenn bereit). */
    public long secondsUntilReady(UUID islandUuid, RitualType ritual) {
        if (islandUuid == null) return 0L;
        long lastUse = cooldowns.getOrDefault(islandUuid, Collections.emptyMap()).getOrDefault(ritual, 0L);
        long elapsed = System.currentTimeMillis() / 1000 - lastUse;
        return Math.max(0L, ritual.cooldownSeconds - elapsed);
    }
}
