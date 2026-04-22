package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ambient;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpawnZoneListener implements Listener {

    private final PHSurvival plugin;

    /** Spieler die sich aktuell in der Spawnzone befinden. */
    private final Set<UUID> inZone = new HashSet<>();

    /** Throttle für Border-Nachrichten: UUID → letzter Zeitstempel ms. */
    private final Map<UUID, Long> borderMsgCooldown = new HashMap<>();

    private static final Component MSG_PROTECTED =
        Component.text("§cDu befindest dich in der Spawn-Schutzzone!");
    private static final Component MSG_BORDER =
        Component.text("§c⚠ Spawn-Bereich – du kannst die Zone nicht verlassen!");

    public SpawnZoneListener(PHSurvival plugin) {
        this.plugin = plugin;

        // Alle 40 Ticks (2s): Tageszeit & Hunger für alle Zone-Spieler sicherstellen
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : Set.copyOf(inZone)) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null) { inZone.remove(uuid); continue; }
                p.setPlayerTime(6000L, false); // Immer Mittag
                p.setPlayerWeather(WeatherType.CLEAR); // Immer klar
                if (p.getFoodLevel() < 20) p.setFoodLevel(20);
                p.setSaturation(20f);
            }
        }, 40L, 40L);
    }

    // ── Zone-Grenzen ─────────────────────────────────────────────────────

    private boolean inSpawnZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        World spawnWorld = plugin.getServer().getWorld(worldName);
        if (spawnWorld == null || !spawnWorld.equals(loc.getWorld())) return false;

        var bm = plugin.getSpawnBorderManager();
        if (bm.hasPolygon()) {
            return bm.isInside(loc.getX(), loc.getZ());
        }
        // Fallback: Kreis aus config (solange kein Polygon gesetzt)
        double radius = plugin.getConfig().getDouble("spawn-zone.radius", 150);
        double cx = plugin.getConfig().getDouble("spawn.x");
        double cz = plugin.getConfig().getDouble("spawn.z");
        double dx = loc.getX() - cx;
        double dz = loc.getZ() - cz;
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * Klemmt X/Z auf den nächstgelegenen Punkt AUF dem Polygon-Rand
     * und schiebt den Spieler minimal nach innen (0.4 Blöcke zum Schwerpunkt).
     */
    private Location clampToBorder(Location to) {
        var bm = plugin.getSpawnBorderManager();
        if (bm.hasPolygon()) {
            double[] nearest  = bm.nearestBoundaryPoint(to.getX(), to.getZ());
            double[] centroid = bm.centroid();
            double dx = centroid[0] - nearest[0];
            double dz = centroid[1] - nearest[1];
            double len = Math.sqrt(dx * dx + dz * dz);
            double push = 0.4;
            if (len > 0) { dx = dx / len * push; dz = dz / len * push; }
            return new Location(to.getWorld(), nearest[0] + dx, to.getY(), nearest[1] + dz, to.getYaw(), to.getPitch());
        }
        // Fallback: Kreis – Spieler auf die Kreislinie schieben
        double radius = plugin.getConfig().getDouble("spawn-zone.radius", 150) - 0.6;
        double cx = plugin.getConfig().getDouble("spawn.x");
        double cz = plugin.getConfig().getDouble("spawn.z");
        double dx = to.getX() - cx;
        double dz = to.getZ() - cz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist == 0) dist = 1;
        double nx = cx + dx / dist * radius;
        double nz = cz + dz / dist * radius;
        return new Location(to.getWorld(), nx, to.getY(), nz, to.getYaw(), to.getPitch());
    }

    private boolean isAdmin(Player p) {
        return p.hasPermission("survival.admin");
    }

    // ── Zonen-Tracking ────────────────────────────────────────────────────

    private void onEnter(Player player) {
        if (inZone.add(player.getUniqueId())) {
            player.setPlayerTime(6000L, false);
            player.setPlayerWeather(WeatherType.CLEAR);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    private void onLeave(Player player) {
        if (inZone.remove(player.getUniqueId())) {
            player.resetPlayerTime();
            player.resetPlayerWeather();
        }
    }

    // ── Border: Spieler kann Zone nicht zu Fuß verlassen ─────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();

        // Nur bei horizontaler Bewegung prüfen (spart Performance)
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) return;

        Player player = event.getPlayer();
        if (isAdmin(player)) return;

        boolean fromIn = inSpawnZone(from);
        boolean toIn   = inSpawnZone(to);

        if (!fromIn && toIn)  { onEnter(player); return; }
        if (fromIn  && !toIn) {
            // Spieler exakt auf die letzte gültige Position zurücksetzen – 100 % dicht
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
            long now = System.currentTimeMillis();
            if (now - borderMsgCooldown.getOrDefault(player.getUniqueId(), 0L) > 3000) {
                player.sendActionBar(MSG_BORDER);
                borderMsgCooldown.put(player.getUniqueId(), now);
            }
        }
    }

    /** Teleportationen (z.B. /home, /back) dürfen die Zone verlassen. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        boolean toIn = inSpawnZone(event.getTo());
        boolean fromIn = inSpawnZone(event.getFrom());
        if (!fromIn && toIn) onEnter(player);
        if (fromIn && !toIn) onLeave(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && inSpawnZone(player.getLocation())) onEnter(player);
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && inSpawnZone(player.getLocation())) onEnter(player);
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onLeave(event.getPlayer());
        borderMsgCooldown.remove(event.getPlayer().getUniqueId());
    }

    // ── Kein Hunger ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!inZone.contains(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    // ── Kein Umgebungs-Schaden (Fall, Feuer, Ersticken …) ────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!inZone.contains(player.getUniqueId())) return;
        // Nur Umgebungs-Schaden canceln – PvP wird in onEntityDamageByEntity gehandelt
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                && cause != EntityDamageEvent.DamageCause.PROJECTILE) {
            event.setCancelled(true);
        }
    }

    // ── Kein PvP, kein Mob-Schaden ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!inZone.contains(victim.getUniqueId())) return;

        // Mob-Angriff
        if (event.getDamager() instanceof Mob
                || (event.getDamager() instanceof Projectile proj
                    && proj.getShooter() instanceof Mob)) {
            event.setCancelled(true);
            return;
        }

        // PvP
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) attacker = p;

        if (attacker == null || isAdmin(attacker)) return;
        event.setCancelled(true);
        attacker.sendActionBar(MSG_PROTECTED);
    }

    // ── Kein Mob-Spawn ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Mob)) return;
        // CUSTOM = von Plugins gespawnt (NPCs, Spawner-Eier via Admin) – nicht blocken
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (inSpawnZone(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    // ── Wetter-Wechsel sofort für Zone-Spieler überschreiben ─────────────

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!event.toWeatherState()) return; // nur wenn Regen beginnt
        String spawnWorld = plugin.getConfig().getString("spawn.world", "world");
        if (!event.getWorld().getName().equals(spawnWorld)) return;
        // 1 Tick warten damit der Wechsel abgeschlossen ist, dann für alle Zone-Spieler überschreiben
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : Set.copyOf(inZone)) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null) p.setPlayerWeather(WeatherType.CLEAR);
            }
        }, 1L);
    }

    // ── Block-Schutz ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isAdmin(event.getPlayer())) return;
        if (inSpawnZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(MSG_PROTECTED);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isAdmin(event.getPlayer())) return;
        if (inSpawnZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(MSG_PROTECTED);
        }
    }
}
