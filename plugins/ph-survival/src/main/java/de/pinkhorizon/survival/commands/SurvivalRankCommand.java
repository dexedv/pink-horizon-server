package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SurvivalRankManager;
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

public class SurvivalRankCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public SurvivalRankCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survival.admin")) {
            sender.sendMessage("\u00a7cKeine Berechtigung!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("\u00a7cVerwendung: /srank <set|info|list> [Spieler] [Rang]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage("\u00a7cVerwendung: /srank set <Spieler> <Rang>");
                    return true;
                }
                if (!SurvivalRankManager.RANKS.containsKey(args[2].toLowerCase())) {
                    sender.sendMessage("\u00a7cUnbekannter Rang! Verfügbare Ränge: " + String.join(", ", SurvivalRankManager.RANKS.keySet()));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                UUID targetUuid = null;
                String targetName = args[1];
                if (target != null) {
                    targetUuid = target.getUniqueId();
                    targetName = target.getName();
                } else {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                    if (offline.hasPlayedBefore()) {
                        targetUuid = offline.getUniqueId();
                        targetName = offline.getName() != null ? offline.getName() : args[1];
                    }
                }
                if (targetUuid == null) {
                    sender.sendMessage("\u00a7cSpieler nicht gefunden!");
                    return true;
                }
                plugin.getRankManager().setRank(targetUuid, targetName, args[2].toLowerCase());
                sender.sendMessage("\u00a7aRang von \u00a7f" + targetName + "\u00a7a auf \u00a7f" + args[2] + "\u00a7a gesetzt.");
                if (target != null) {
                    plugin.getRankManager().applyTabName(target);
                    target.sendMessage("\u00a7aDein Rang wurde auf \u00a7f" + args[2] + "\u00a7a gesetzt.");
                }
            }
            case "info" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage("\u00a7cSpieler nicht online!");
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("\u00a7cBitte einen Spieler angeben!");
                    return true;
                }
                SurvivalRankManager.Rank rank = plugin.getRankManager().getRank(target.getUniqueId());
                sender.sendMessage("\u00a7dRanginfo von \u00a7f" + target.getName() + "\u00a7d:");
                sender.sendMessage("\u00a77Rang: \u00a7f" + rank.id);
                sender.sendMessage("\u00a77Max Claims: \u00a7f" + rank.maxClaims);
                sender.sendMessage("\u00a77Max Homes: \u00a7f" + rank.maxHomes);
                sender.sendMessage("\u00a77Priorität: \u00a7f" + rank.priority);
            }
            case "list" -> {
                sender.sendMessage("\u00a7dVerfügbare Ränge:");
                for (SurvivalRankManager.Rank rank : SurvivalRankManager.RANKS.values()) {
                    sender.sendMessage("\u00a77- \u00a7f" + rank.id + " \u00a78(Claims: " + rank.maxClaims
                            + ", Homes: " + rank.maxHomes + ")");
                }
            }
            default -> sender.sendMessage("\u00a7cVerwendung: /srank <set|info|list> [Spieler] [Rang]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filterList(List.of("set", "info", "list"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("info"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return filterList(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filterList(new ArrayList<>(SurvivalRankManager.RANKS.keySet()), args[2]);
        }
        return List.of();
    }

    private List<String> filterList(List<String> list, String input) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(input.toLowerCase())) result.add(s);
        }
        return result;
    }
}
