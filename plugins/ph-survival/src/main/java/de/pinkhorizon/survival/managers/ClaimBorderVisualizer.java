package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClaimBorderVisualizer {

    private final PHSurvival plugin;
    private BukkitTask task;
    private final Set<UUID> optedOut = new HashSet<>();

    private static final Particle.DustOptions OWN     = new Particle.DustOptions(Color.fromRGB(0, 220, 0),   1.5f);
    private static final Particle.DustOptions TRUSTED = new Particle.DustOptions(Color.fromRGB(0, 150, 255), 1.5f);
    private static final Particle.DustOptions OTHER   = new Particle.DustOptions(Color.fromRGB(220, 0, 0),   1.5f);

    public ClaimBorderVisualizer(PHSurvival plugin) {
        this.plugin = plugin;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                showNearby(player);
            }
        }, 40L, 40L);
    }

    /** Toggles the border visibility for a player. Returns true if now visible. */
    public boolean toggle(UUID uuid) {
        if (optedOut.remove(uuid)) return true;
        optedOut.add(uuid);
        return false;
    }

    private void showNearby(Player player) {
        UUID uuid   = player.getUniqueId();
        if (optedOut.contains(uuid)) return;
        World world = player.getWorld();
        double y    = player.getLocation().getY() + 1.5;
        int pcx     = player.getLocation().getChunk().getX();
        int pcz     = player.getLocation().getChunk().getZ();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!world.isChunkLoaded(cx, cz)) continue;
                Chunk chunk = world.getChunkAt(cx, cz);
                if (!plugin.getClaimManager().isClaimed(chunk)) continue;

                UUID owner = plugin.getClaimManager().getOwner(chunk);
                Particle.DustOptions color;
                if (owner.equals(uuid)) {
                    color = OWN;
                } else if (plugin.getClaimManager().isTrusted(chunk, uuid)) {
                    color = TRUSTED;
                } else {
                    color = OTHER;
                }

                drawEdges(player, world, cx, cz, y, owner, color);
            }
        }
    }

    // Zeichnet nur die Kanten, die an einen anders-geclaimen (oder ungeclaimten) Chunk grenzen.
    private void drawEdges(Player player, World world, int cx, int cz, double y,
                           UUID owner, Particle.DustOptions color) {
        int x0 = cx * 16;
        int z0 = cz * 16;

        // Nord (z = z0)
        if (!sameOwner(world, cx, cz - 1, owner))
            for (int i = 0; i <= 16; i++)
                player.spawnParticle(Particle.DUST, x0 + i + 0.5, y, z0 + 0.5, 1, 0, 0, 0, 0, color);

        // Süd (z = z0+16)
        if (!sameOwner(world, cx, cz + 1, owner))
            for (int i = 0; i <= 16; i++)
                player.spawnParticle(Particle.DUST, x0 + i + 0.5, y, z0 + 16 + 0.5, 1, 0, 0, 0, 0, color);

        // West (x = x0)
        if (!sameOwner(world, cx - 1, cz, owner))
            for (int i = 0; i <= 16; i++)
                player.spawnParticle(Particle.DUST, x0 + 0.5, y, z0 + i + 0.5, 1, 0, 0, 0, 0, color);

        // Ost (x = x0+16)
        if (!sameOwner(world, cx + 1, cz, owner))
            for (int i = 0; i <= 16; i++)
                player.spawnParticle(Particle.DUST, x0 + 16 + 0.5, y, z0 + i + 0.5, 1, 0, 0, 0, 0, color);
    }

    private boolean sameOwner(World world, int cx, int cz, UUID owner) {
        if (!world.isChunkLoaded(cx, cz)) return false;
        return owner.equals(plugin.getClaimManager().getOwner(world.getChunkAt(cx, cz)));
    }

    public void stop() {
        if (task != null) task.cancel();
    }
}
