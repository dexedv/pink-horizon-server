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
import java.util.UUID;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public HomeCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cNur Spieler!");
            return true;
        }

        UUID uuid = player.getUniqueId();

        switch (label.toLowerCase()) {
            case "sethome" -> {
                String name = args.length > 0 ? args[0].toLowerCase() : "home";
                int maxHomes = plugin.getRankManager().getMaxHomes(uuid);
                Map<String, Location> existing = plugin.getHomeManager().getHomes(uuid);
                if (!existing.containsKey(name) && existing.size() >= maxHomes) {
                    player.sendMessage("\u00a7cDu hast das Maximum von \u00a7f" + maxHomes + "\u00a7c Homes erreicht!");
                    return true;
                }
                plugin.getHomeManager().setHome(uuid, name, player.getLocation());
                player.sendMessage("\u00a7aHome \u00a7f" + name + "\u00a7a gesetzt!");
            }
            case "home" -> {
                String name = args.length > 0 ? args[0].toLowerCase() : "home";
                Location loc = plugin.getHomeManager().getHome(uuid, name);
                if (loc == null) {
                    player.sendMessage("\u00a7cHome \u00a7f" + name + "\u00a7c existiert nicht!");
                    return true;
                }
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> player.teleport(loc), 1L);
                player.sendMessage("\u00a7aTeleportiert zu Home \u00a7f" + name + "\u00a7a!");
            }
            case "delhome" -> {
                String name = args.length > 0 ? args[0].toLowerCase() : "home";
                if (plugin.getHomeManager().deleteHome(uuid, name)) {
                    player.sendMessage("\u00a7aHome \u00a7f" + name + "\u00a7a gel\u00f6scht!");
                } else {
                    player.sendMessage("\u00a7cHome \u00a7f" + name + "\u00a7c nicht gefunden!");
                }
            }
            case "homes" -> {
                Map<String, Location> playerHomes = plugin.getHomeManager().getHomes(uuid);
                int maxHomes = plugin.getRankManager().getMaxHomes(uuid);
                if (playerHomes.isEmpty()) {
                    player.sendMessage("\u00a77Du hast noch keine Homes gesetzt.");
                } else {
                    player.sendMessage("\u00a7dDeine Homes \u00a78(\u00a7f" + playerHomes.size() + "\u00a78/\u00a7f" + maxHomes + "\u00a78):");
                    for (String homeName : playerHomes.keySet()) {
                        Location loc = playerHomes.get(homeName);
                        player.sendMessage("\u00a77- \u00a7f" + homeName + " \u00a78(\u00a77" + loc.getWorld().getName()
                                + " " + (int) loc.getX() + " " + (int) loc.getY() + " " + (int) loc.getZ() + "\u00a78)");
                    }
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1 && (label.equalsIgnoreCase("home") || label.equalsIgnoreCase("delhome"))) {
            List<String> names = new ArrayList<>(plugin.getHomeManager().getHomes(player.getUniqueId()).keySet());
            String input = args[0].toLowerCase();
            names.removeIf(n -> !n.startsWith(input));
            return names;
        }
        return List.of();
    }
}
