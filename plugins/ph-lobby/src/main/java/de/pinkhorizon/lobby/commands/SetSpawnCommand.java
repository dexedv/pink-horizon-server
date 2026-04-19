package de.pinkhorizon.lobby.commands;

import de.pinkhorizon.lobby.PHLobby;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    private final PHLobby plugin;

    public SetSpawnCommand(PHLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }
        if (!player.hasPermission("lobby.admin")) {
            player.sendMessage("\u00a7cKeine Berechtigung!");
            return true;
        }

        Location loc = player.getLocation();

        plugin.getConfig().set("spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.x",     loc.getX());
        plugin.getConfig().set("spawn.y",     loc.getY());
        plugin.getConfig().set("spawn.z",     loc.getZ());
        plugin.getConfig().set("spawn.yaw",   (double) loc.getYaw());
        plugin.getConfig().set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();

        player.sendMessage(String.format(
            "\u00a7aSpawn gesetzt auf \u00a7fX:%.2f Y:%.2f Z:%.2f",
            loc.getX(), loc.getY(), loc.getZ()));
        return true;
    }
}
