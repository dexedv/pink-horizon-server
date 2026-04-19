package de.pinkhorizon.minigames.commands;

import de.pinkhorizon.minigames.PHMinigames;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class StatsCommand implements CommandExecutor {

    private final PHMinigames plugin;

    public StatsCommand(PHMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }

        UUID uuid = player.getUniqueId();
        player.sendMessage("\u00a7d=== Deine Statistiken ===");
        player.sendMessage("\u00a77BedWars Wins:  \u00a7f" + plugin.getStatsManager().getStat(uuid, "bedwars_wins"));
        player.sendMessage("\u00a77BedWars Kills: \u00a7f" + plugin.getStatsManager().getStat(uuid, "bedwars_kills"));
        player.sendMessage("\u00a77SkyWars Wins:  \u00a7f" + plugin.getStatsManager().getStat(uuid, "skywars_wins"));
        player.sendMessage("\u00a77SkyWars Kills: \u00a7f" + plugin.getStatsManager().getStat(uuid, "skywars_kills"));
        return true;
    }
}
