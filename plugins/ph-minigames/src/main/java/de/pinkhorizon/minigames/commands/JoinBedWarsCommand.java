package de.pinkhorizon.minigames.commands;

import de.pinkhorizon.minigames.PHMinigames;
import de.pinkhorizon.minigames.games.GameType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JoinBedWarsCommand implements CommandExecutor {

    private final PHMinigames plugin;

    public JoinBedWarsCommand(PHMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }

        if (plugin.getArenaManager().joinGame(player.getUniqueId(), GameType.BEDWARS)) {
            player.sendMessage("\u00a7aBedWars-Arena beigetreten!");
        } else {
            player.sendMessage("\u00a7cKeine freie BedWars-Arena verfuegbar. Bitte warten...");
        }
        return true;
    }
}
