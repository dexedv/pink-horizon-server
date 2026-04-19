package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class RtpCommand implements CommandExecutor {

    private static final int  MAX_RANGE     = 10_000; // Maximale Entfernung von 0/0
    private static final int  MIN_RANGE     = 300;    // Mindestabstand zum Spawn
    private static final long COOLDOWN_MS   = 30 * 60 * 1000L; // 30 Minuten
    private static final int  COUNTDOWN_SEC = 3;

    private static final Set<Material> UNSAFE = Set.of(
        Material.LAVA, Material.WATER, Material.FIRE,
        Material.CACTUS, Material.SWEET_BERRY_BUSH,
        Material.POWDER_SNOW, Material.MAGMA_BLOCK
    );

    private final PHSurvival plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public RtpCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler!");
            return true;
        }

        UUID uuid = player.getUniqueId();

        // Cooldown prüfen (OPs sind exempt)
        if (!player.isOp()) {
            long lastUsed = cooldowns.getOrDefault(uuid, 0L);
            long remaining = COOLDOWN_MS - (System.currentTimeMillis() - lastUsed);
            if (remaining > 0) {
                long min = remaining / 60000;
                long sec = (remaining % 60000) / 1000;
                player.sendMessage("§cRTP Cooldown: §f" + min + "m " + sec + "s");
                return true;
            }
        }

        // Bereits wartend?
        if (pending.containsKey(uuid)) {
            player.sendMessage("§cDu teleportierst dich bereits!");
            return true;
        }

        // Safe Location suchen (async für Performance)
        player.sendMessage("§dSuche sichere Position...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location target = findSafeLocation(player.getWorld());
            if (target == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§cKeine sichere Position gefunden! Bitte erneut versuchen."));
                return;
            }

            // Countdown auf Haupt-Thread
            Bukkit.getScheduler().runTask(plugin, () -> startCountdown(player, target));
        });

        return true;
    }

    private void startCountdown(Player player, Location target) {
        UUID uuid = player.getUniqueId();
        Location startLoc = player.getLocation().clone();

        player.sendMessage("§dTeleportiere in §f" + COUNTDOWN_SEC + " §dSekunden — nicht bewegen!");

        int[] remaining = {COUNTDOWN_SEC};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelPending(uuid);
                return;
            }

            // Bewegung prüfen
            Location cur = player.getLocation();
            if (Math.abs(cur.getX() - startLoc.getX()) > 0.5
                    || Math.abs(cur.getZ() - startLoc.getZ()) > 0.5) {
                player.sendMessage("§cTeleportation abgebrochen — du hast dich bewegt!");
                cancelPending(uuid);
                return;
            }

            remaining[0]--;
            if (remaining[0] > 0) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§dTeleportiere in §f" + remaining[0] + "§d..."));
                return;
            }

            // Teleportieren
            cancelPending(uuid);
            player.teleport(target);
            cooldowns.put(uuid, System.currentTimeMillis());
            plugin.getAfkManager().resetAfk(player);

            player.sendMessage("§aTeleportiert zu §fX:" + target.getBlockX()
                + " Y:" + target.getBlockY() + " Z:" + target.getBlockZ());
            player.sendActionBar(net.kyori.adventure.text.Component.text("§aViel Spaß beim Erkunden!"));
            player.playSound(target, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);

        }, 20L, 20L);

        pending.put(uuid, task);
    }

    private void cancelPending(UUID uuid) {
        BukkitTask t = pending.remove(uuid);
        if (t != null) t.cancel();
    }

    private Location findSafeLocation(World world) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = randomCoord();
            int z = randomCoord();

            // Höchsten Block holen
            int y = world.getHighestBlockYAt(x, z);
            if (y < 60 || y > 250) continue;

            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            Material ground = world.getBlockAt(x, y, z).getType();
            Material feet   = world.getBlockAt(x, y + 1, z).getType();
            Material head   = world.getBlockAt(x, y + 2, z).getType();

            if (!ground.isSolid()) continue;
            if (UNSAFE.contains(ground)) continue;
            if (!feet.isAir() || !head.isAir()) continue;

            return loc;
        }
        return null;
    }

    private int randomCoord() {
        int sign = random.nextBoolean() ? 1 : -1;
        return sign * (MIN_RANGE + random.nextInt(MAX_RANGE - MIN_RANGE));
    }
}
