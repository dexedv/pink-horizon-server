package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public SpawnCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cNur Spieler!");
            return true;
        }

        if (label.equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("survival.admin")) {
                player.sendMessage("\u00a7cKeine Berechtigung!");
                return true;
            }
            Location loc = player.getLocation();
            plugin.getConfig().set("spawn.world", loc.getWorld().getName());
            plugin.getConfig().set("spawn.x", loc.getX());
            plugin.getConfig().set("spawn.y", loc.getY());
            plugin.getConfig().set("spawn.z", loc.getZ());
            plugin.getConfig().set("spawn.yaw", loc.getYaw());
            plugin.getConfig().set("spawn.pitch", loc.getPitch());
            plugin.saveConfig();
            player.sendMessage("\u00a7aSpawn wurde gesetzt!");
            return true;
        }

        // /spawn
        String worldName = plugin.getConfig().getString("spawn.world");
        if (worldName == null) {
            player.sendMessage("\u00a7cKein Spawn gesetzt! Nutze /setspawn.");
            return true;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("\u00a7cSpawn-Welt nicht gefunden!");
            return true;
        }
        double x = plugin.getConfig().getDouble("spawn.x");
        double y = plugin.getConfig().getDouble("spawn.y");
        double z = plugin.getConfig().getDouble("spawn.z");
        float yaw = (float) plugin.getConfig().getDouble("spawn.yaw");
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch");
        player.teleport(new Location(world, x, y, z, yaw, pitch));
        player.sendMessage("\u00a7aTeleportiert zum Spawn!");
        return true;
    }
}
