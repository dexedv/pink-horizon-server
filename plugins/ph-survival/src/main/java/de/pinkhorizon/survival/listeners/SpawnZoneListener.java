package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SpawnZoneListener implements Listener {

    private final PHSurvival plugin;

    private static final Component MSG = Component.text(
        "§cDu befindest dich in der Spawn-Schutzzone!", NamedTextColor.RED);

    public SpawnZoneListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Schutzzone berechnen ─────────────────────────────────────────────

    private boolean inSpawnZone(Location loc) {
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        World spawnWorld  = plugin.getServer().getWorld(worldName);
        if (spawnWorld == null || !spawnWorld.equals(loc.getWorld())) return false;

        double radius = plugin.getConfig().getDouble("spawn-zone.radius", 50);
        double cx = plugin.getConfig().getDouble("spawn.x");
        double cz = plugin.getConfig().getDouble("spawn.z");

        return Math.abs(loc.getX() - cx) <= radius
            && Math.abs(loc.getZ() - cz) <= radius;
    }

    private boolean isAdmin(Player player) {
        return player.hasPermission("survival.admin");
    }

    // ── Block-Schutz ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (isAdmin(event.getPlayer())) return;
        if (inSpawnZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MSG);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (isAdmin(event.getPlayer())) return;
        if (inSpawnZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MSG);
        }
    }

    // ── Interaktionsschutz (Kisten, Türen etc.) ──────────────────────────
    // Kisten/Blöcke in der Spawn-Zone bleiben zugänglich (Spieler sollen
    // z.B. NPCs und Spawn-Shops nutzen). Nur Zerstörung ist gesperrt.

    // ── PvP + Mob-Schaden-Schutz ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!inSpawnZone(victim.getLocation())) return;

        // Mob-Angriff
        if (event.getDamager() instanceof Mob
                || (event.getDamager() instanceof Projectile proj
                    && proj.getShooter() instanceof Mob)) {
            event.setCancelled(true);
            return;
        }

        // PvP
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;
        if (isAdmin(attacker)) return;

        event.setCancelled(true);
        attacker.sendMessage(MSG);
    }
}
