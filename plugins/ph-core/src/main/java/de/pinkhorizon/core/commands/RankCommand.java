package de.pinkhorizon.core.commands;

import de.pinkhorizon.core.PHCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class RankCommand implements CommandExecutor, TabCompleter {

    private static final List<String> VALID_RANKS = List.of(
        "spieler", "vip", "vip_plus", "mvp", "mvp_plus", "legende"
    );

    private final PHCore plugin;

    public RankCommand(PHCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pinkhorizon.admin")) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) {
            sender.sendMessage("§eVerwendung: /phrank set <Spieler> <Rang>");
            sender.sendMessage("§eRänge: " + String.join(", ", VALID_RANKS));
            return true;
        }

        String targetName = args[1];
        String rankId     = args[2].toLowerCase();

        if (!VALID_RANKS.contains(rankId)) {
            sender.sendMessage("§cUnbekannter Rang: §e" + rankId);
            sender.sendMessage("§7Gültig: " + String.join(", ", VALID_RANKS));
            return true;
        }

        // Spieler online → UUID direkt verfügbar
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            plugin.getRankRepository().setRank(online.getUniqueId(), online.getName(), rankId);
            String display = plugin.getConfig().getString("ranks." + rankId + ".display", rankId);
            sender.sendMessage("§aRang gesetzt: §f" + online.getName() + " §8→ " + display.replace("&", "§"));
            online.sendMessage("§dDein Rang wurde auf §r" + display.replace("&", "§") + " §dgesetzt!");
            return true;
        }

        // Spieler offline → UUID via Bukkit-Cache (ggf. nicht gefunden)
        sender.sendMessage("§cSpieler §e" + targetName + " §cist nicht online. Rang kann nur für Online-Spieler gesetzt werden.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("set");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName).toList();
        if (args.length == 3) return VALID_RANKS;
        return List.of();
    }
}
