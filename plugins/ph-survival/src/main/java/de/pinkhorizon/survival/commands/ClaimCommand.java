package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class ClaimCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public ClaimCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        UUID uuid = player.getUniqueId();

        if (label.equalsIgnoreCase("unclaim")) {
            if (plugin.getClaimManager().unclaim(chunk, uuid)) {
                player.sendMessage("\u00a7aChunk-Claim entfernt.");
            } else {
                player.sendMessage("\u00a7cDu besitzt diesen Chunk nicht.");
            }
            return true;
        }

        if (label.equalsIgnoreCase("claimlist")) {
            Set<String> playerClaims = plugin.getClaimManager().getPlayerClaims(uuid);
            int maxClaims = plugin.getRankManager().getMaxClaims(uuid);
            if (playerClaims.isEmpty()) {
                player.sendMessage("\u00a77Du hast keine geclaimten Chunks.");
            } else {
                player.sendMessage("\u00a7dDeine Claims \u00a78(\u00a7f" + playerClaims.size() + "\u00a78/\u00a7f" + maxClaims + "\u00a78):");
                for (String key : playerClaims) {
                    player.sendMessage("\u00a77- \u00a7f" + key);
                }
            }
            return true;
        }

        // /claim
        // Spawn-Schutzzone prüfen (200 Blöcke)
        Location spawnLoc = getSpawnLocation();
        if (spawnLoc != null && spawnLoc.getWorld() != null
                && spawnLoc.getWorld().equals(player.getWorld())) {
            double chunkCenterX = chunk.getX() * 16 + 8;
            double chunkCenterZ = chunk.getZ() * 16 + 8;
            double dist = Math.sqrt(Math.pow(chunkCenterX - spawnLoc.getX(), 2)
                    + Math.pow(chunkCenterZ - spawnLoc.getZ(), 2));
            if (dist <= 200 && !player.isOp()) {
                player.sendMessage("§cIm Spawn-Bereich (200 Blöcke) darf nicht geclaimed werden!");
                return true;
            }
        }

        long price = plugin.getConfig().getLong("claims.claim-price", 100);
        if (!plugin.getEconomyManager().withdraw(uuid, price)) {
            player.sendMessage("\u00a7cNicht genug Coins! Preis: " + price);
            return true;
        }

        int maxClaims = plugin.getRankManager().getMaxClaims(uuid);
        if (plugin.getClaimManager().claim(chunk, uuid, maxClaims)) {
            player.sendMessage("\u00a7aChunk geclaimed! (-" + price + " Coins)");
            player.sendMessage("\u00a77Claims: " + plugin.getClaimManager().getClaimCount(uuid)
                    + "/" + maxClaims);
        } else {
            plugin.getEconomyManager().deposit(uuid, price); // Rueckerstattung
            player.sendMessage("\u00a7cChunk bereits geclaimed oder Limit erreicht!");
        }
        return true;
    }

    private Location getSpawnLocation() {
        String worldName = plugin.getConfig().getString("spawn.world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
            plugin.getConfig().getDouble("spawn.x"),
            plugin.getConfig().getDouble("spawn.y"),
            plugin.getConfig().getDouble("spawn.z"));
    }
}
