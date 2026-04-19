package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WarpCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public WarpCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cNur Spieler!");
            return true;
        }

        switch (label.toLowerCase()) {
            case "setwarp" -> {
                if (!player.hasPermission("survival.admin")) {
                    player.sendMessage("\u00a7cKeine Berechtigung!");
                    return true;
                }
                if (args.length < 1) {
                    player.sendMessage("\u00a7cVerwendung: /setwarp <Name>");
                    return true;
                }
                plugin.getWarpManager().setWarp(args[0], player.getLocation());
                player.sendMessage("\u00a7aWarp \u00a7f" + args[0] + "\u00a7a gesetzt!");
            }
            case "warp" -> {
                if (args.length < 1) {
                    player.sendMessage("\u00a7cVerwendung: /warp <Name>");
                    return true;
                }
                Location loc = plugin.getWarpManager().getWarp(args[0]);
                if (loc == null) {
                    player.sendMessage("\u00a7cWarp \u00a7f" + args[0] + "\u00a7c existiert nicht!");
                    return true;
                }
                player.teleport(loc);
                player.sendMessage("\u00a7aTeleportiert zu Warp \u00a7f" + args[0] + "\u00a7a!");
            }
            case "delwarp" -> {
                if (!player.hasPermission("survival.admin")) {
                    player.sendMessage("\u00a7cKeine Berechtigung!");
                    return true;
                }
                if (args.length < 1) {
                    player.sendMessage("\u00a7cVerwendung: /delwarp <Name>");
                    return true;
                }
                if (plugin.getWarpManager().deleteWarp(args[0])) {
                    player.sendMessage("\u00a7aWarp \u00a7f" + args[0] + "\u00a7a gel\u00f6scht!");
                } else {
                    player.sendMessage("\u00a7cWarp \u00a7f" + args[0] + "\u00a7c nicht gefunden!");
                }
            }
            case "warps" -> {
                Map<String, Location> warps = plugin.getWarpManager().getWarps();
                if (warps.isEmpty()) {
                    player.sendMessage("\u00a77Keine Warps vorhanden.");
                } else {
                    player.sendMessage("\u00a7dVeuf\u00fcgbare Warps:");
                    for (String name : warps.keySet()) {
                        player.sendMessage("\u00a77- \u00a7f" + name);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && (label.equalsIgnoreCase("warp") || label.equalsIgnoreCase("delwarp"))) {
            List<String> names = new ArrayList<>(plugin.getWarpManager().getWarps().keySet());
            String input = args[0].toLowerCase();
            names.removeIf(n -> !n.startsWith(input));
            return names;
        }
        return List.of();
    }
}
