package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.enums.IslandGene;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import world.bentobox.bentobox.database.objects.Island;

import java.sql.*;
import java.util.*;

/**
 * Sternschnuppen-System: Jede Minecraft-Nacht fallen 1-3 Stern-Fragmente
 * auf zufällige Inseln. Belohnt aktive Spieler.
 */
public class StarManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum StarTier { COMMON, RARE, EPIC, LEGENDARY }

    private final PHSkyBlock plugin;
    private final Random rng = new Random();
    private final NamespacedKey STAR_KEY;

    // Tier-Gewichtungen
    private static final int[] TIER_WEIGHTS = {55, 30, 12, 3}; // COMMON, RARE, EPIC, LEGENDARY

    public StarManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        STAR_KEY = new NamespacedKey(plugin, "star_tier");
        createTables();
        startNightWatcher();
    }

    // ── Tabellen ──────────────────────────────────────────────────────────────

    private void createTables() {
        try (Connection c = plugin.getDatabase().getConnection();
             Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_stars (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    island_uuid VARCHAR(36)  NOT NULL,
                    tier        VARCHAR(32)  NOT NULL,
                    dropped_at  BIGINT       NOT NULL,
                    collected   TINYINT      DEFAULT 0
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Star-Tabelle Fehler: " + e.getMessage());
        }
    }

    // ── Nacht-Watcher ─────────────────────────────────────────────────────────

    private boolean nightTriggered = false;

    private void startNightWatcher() {
        new BukkitRunnable() {
            @Override public void run() {
                var worldOpt = BentoBoxHook.getSkyBlockWorld();
                if (worldOpt.isEmpty()) return;
                long time = worldOpt.get().getTime();
                boolean isNight = time >= 13000 && time <= 13100; // Kurzes Fenster direkt nach Sonnenuntergang
                if (isNight && !nightTriggered) {
                    nightTriggered = true;
                    dropNightlyStars();
                } else if (!isNight) {
                    nightTriggered = false;
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void dropNightlyStars() {
        if (!BentoBoxHook.isAvailable()) return;
        var worldOpt = BentoBoxHook.getSkyBlockWorld();
        if (worldOpt.isEmpty()) return;

        // Alle online Spieler mit Insel sammeln
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) return;

        int stars = 1 + rng.nextInt(3); // 1-3 Sterne

        for (int i = 0; i < stars; i++) {
            // Zufälligen Spieler wählen (Starbound-Gen erhöht Chance)
            Player target = pickTarget(players);
            StarTier tier = rollTier(target);
            dropStarOnPlayer(target, tier);
        }
    }

    private Player pickTarget(List<Player> players) {
        // Starbound-Spieler haben doppeltes Gewicht
        List<Player> weighted = new ArrayList<>();
        for (Player p : players) {
            weighted.add(p);
            if (plugin.getIslandDnaManager().isStarbound(p.getUniqueId())) {
                weighted.add(p); // doppelt
            }
        }
        return weighted.get(rng.nextInt(weighted.size()));
    }

    private StarTier rollTier(Player player) {
        int total = 0;
        for (int w : TIER_WEIGHTS) total += w;
        int roll = rng.nextInt(total);
        int cum  = 0;
        StarTier[] tiers = StarTier.values();
        for (int i = 0; i < tiers.length; i++) {
            cum += TIER_WEIGHTS[i];
            if (roll < cum) return tiers[i];
        }
        return StarTier.COMMON;
    }

    private void dropStarOnPlayer(Player player, StarTier tier) {
        var islandOpt = BentoBoxHook.getIsland(player.getUniqueId());
        if (islandOpt.isEmpty()) return;
        Island island = islandOpt.get();

        Location center = island.getCenter();
        if (center == null) return;

        // Stern-Item erstellen
        ItemStack star = createStarItem(tier);

        // Spawn-Position: zufällig auf der Insel
        double range = island.getProtectionRange() * 0.7;
        double dx    = (rng.nextDouble() - 0.5) * 2 * range;
        double dz    = (rng.nextDouble() - 0.5) * 2 * range;
        Location dropLoc = center.clone().add(dx, 2, dz);
        dropLoc.setY(findSurface(dropLoc));

        // Partikel + Sound ankündigen
        player.getWorld().spawnParticle(Particle.FIREWORK, dropLoc, 30, 0.5, 1, 0.5, 0.05);
        player.playSound(dropLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.5f);

        // Meldung
        String tierStr = switch (tier) {
            case COMMON    -> "<gray>Gewöhnlicher";
            case RARE      -> "<aqua>Seltener";
            case EPIC      -> "<light_purple>Epischer";
            case LEGENDARY -> "<gold><bold>LEGENDÄRER";
        };
        player.sendMessage(MM.deserialize(
            "<yellow>⭐ Ein " + tierStr + " <yellow>Stern ist auf deiner Insel gelandet!"));

        // Server-Ankündigung bei Legendary
        if (tier == StarTier.LEGENDARY) {
            String msg = "<gold>⭐ <yellow>Ein <gold><bold>LEGENDÄRER Stern</bold> "
                + "<yellow>ist auf <white>" + player.getName() + "<yellow>s Insel gelandet! ⭐";
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(MM.deserialize(msg)));
        }

        // Item droppen (5 Minuten Despawn)
        var dropped = player.getWorld().dropItem(dropLoc, star);
        dropped.setPickupDelay(40);

        // DB loggen
        logStar(player.getUniqueId(), tier);

        // Auto-Despawn nach 5 Minuten
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dropped.isValid()) dropped.remove();
        }, 20L * 60 * 5);
    }

    private int findSurface(Location loc) {
        int y = 100;
        while (y > 0 && loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType() == Material.AIR) {
            y--;
        }
        return y + 1;
    }

    private void logStar(UUID islandUuid, StarTier tier) {
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO sky_stars (island_uuid, tier, dropped_at) VALUES(?,?,?)")) {
            ps.setString(1, islandUuid.toString());
            ps.setString(2, tier.name());
            ps.setLong  (3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Star loggen fehlgeschlagen: " + e.getMessage());
        }
    }

    // ── Item-Erstellung ───────────────────────────────────────────────────────

    public ItemStack createStarItem(StarTier tier) {
        Material mat = switch (tier) {
            case COMMON    -> Material.GLOWSTONE_DUST;
            case RARE      -> Material.AMETHYST_SHARD;
            case EPIC      -> Material.NETHER_STAR;
            case LEGENDARY -> Material.NETHER_STAR;
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String tierColor = switch (tier) {
            case COMMON    -> "<gray>";
            case RARE      -> "<aqua>";
            case EPIC      -> "<light_purple>";
            case LEGENDARY -> "<gold>";
        };
        String tierName = switch (tier) {
            case COMMON    -> "Gewöhnliches Stern-Fragment";
            case RARE      -> "Seltenes Stern-Fragment";
            case EPIC      -> "Episches Stern-Fragment";
            case LEGENDARY -> "LEGENDÄRES Stern-Fragment";
        };

        meta.displayName(MM.deserialize(tierColor + "<bold>" + tierName));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<dark_gray>Vom Himmel gefallen"));
        lore.add(MM.deserialize(tierColor + "◆ " + tier.name()));
        lore.add(MM.deserialize("<gray>Verwendung: Crafting, DNA, Handel"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(STAR_KEY, PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Gibt den Tier eines Stern-Items zurück (null wenn kein Stern). */
    public StarTier getStarTier(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        var data = item.getItemMeta().getPersistentDataContainer()
            .get(STAR_KEY, PersistentDataType.STRING);
        if (data == null) return null;
        try { return StarTier.valueOf(data); } catch (IllegalArgumentException e) { return null; }
    }

    /** Belohnungen beim Aufsammeln eines Stern-Fragments. */
    public void onStarCollected(Player player, StarTier tier) {
        long coins = switch (tier) {
            case COMMON    -> 500  + rng.nextInt(1500);
            case RARE      -> 5000 + rng.nextInt(15000);
            case EPIC      -> 0; // kein direktes Geld, stattdessen Item
            case LEGENDARY -> 0;
        };
        if (coins > 0) {
            plugin.getCoinManager().addCoins(player.getUniqueId(), coins);
            player.sendMessage(MM.deserialize(
                "<gold>⭐ Stern gesammelt! <gray>+" + String.format("%,d", coins) + " Coins"));
        }

        // Epic/Legendary: DNA-Fragment
        if (tier == StarTier.EPIC || tier == StarTier.LEGENDARY) {
            plugin.getIslandDnaManager().addFragment(player.getUniqueId(), "star_" + tier.name().toLowerCase(), 1);
            player.sendMessage(MM.deserialize(
                "<light_purple>⭐ DNA-Fragment erhalten!"));
        }

        // Chronicle-Eintrag
        plugin.getChronicleManager().addEntry(player.getUniqueId(),
            "star_collected", "Stern-Fragment gefangen: " + tier.name());
    }
}
