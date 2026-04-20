package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public StatsCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        UUID target;
        String name;

        if (args.length >= 1) {
            Player onlineTarget = Bukkit.getPlayer(args[0]);
            OfflinePlayer op = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayerIfCached(args[0]);
            if (op == null || !op.hasPlayedBefore()) {
                sender.sendMessage("§cSpieler nicht gefunden!");
                return true;
            }
            target = op.getUniqueId();
            name = op.getName() != null ? op.getName() : args[0];
        } else if (sender instanceof Player p) {
            target = p.getUniqueId();
            name = p.getName();
        } else {
            sender.sendMessage("§cBitte einen Spielernamen angeben!");
            return true;
        }

        StatsManager stats = plugin.getStatsManager();
        long playtime = stats.getPlaytime(target);
        long hours = playtime / 60;
        long minutes = playtime % 60;

        sender.sendMessage("§d§l--- Stats: §f" + name + " §d§l---");
        sender.sendMessage("§7Spielzeit: §f" + hours + "h " + minutes + "m");
        sender.sendMessage("§7Tode: §f" + stats.getDeaths(target));
        sender.sendMessage("§7Mob-Kills: §f" + stats.getMobKills(target));
        sender.sendMessage("§7Spieler-Kills: §f" + stats.getPlayerKills(target));
        sender.sendMessage("§7Blöcke abgebaut: §f" + stats.getBlocksBroken(target));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return names.stream().filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
