package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.listeners.SurvivalDeathListener;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BackCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cNur Spieler!");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage("§cDieser Befehl ist nicht verfügbar!");
            return true;
        }

        Location lastDeath = SurvivalDeathListener.lastDeaths.get(player.getUniqueId());
        if (lastDeath == null) {
            player.sendMessage("\u00a7cKein Sterbeort gefunden!");
            return true;
        }

        player.teleport(lastDeath);
        player.sendMessage("\u00a7aTeleportiert zu deinem letzten Sterbeort!");
        return true;
    }
}
