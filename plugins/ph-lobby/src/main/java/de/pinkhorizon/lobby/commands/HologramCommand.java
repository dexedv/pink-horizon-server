package de.pinkhorizon.lobby.commands;

import de.pinkhorizon.lobby.PHLobby;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HologramCommand implements CommandExecutor, TabCompleter {

    private final PHLobby plugin;

    public HologramCommand(PHLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cKeine Berechtigung!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cVerwendung: /hologram <create|delete|list> [Name] [Scale] [Text...]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cNur für Spieler!");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§cVerwendung: /hologram create <Name> <Scale> <Text...>");
                    player.sendMessage("§7Beispiel: /hologram create survival 3.0 <bold><green>Survival</green></bold>");
                    return true;
                }
                String name = args[1];
                float scale;
                try {
                    scale = Float.parseFloat(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cScale muss eine Zahl sein (z.B. 3.0)!");
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                if (text.isBlank()) text = "<bold><white>" + name + "</white></bold>";

                plugin.getHologramManager().create(name, player.getLocation(), text, scale);
                player.sendMessage("§aHologram §f'" + name + "' §aerstellt!");
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cVerwendung: /hologram delete <Name>");
                    return true;
                }
                boolean removed = plugin.getHologramManager().remove(args[1]);
                sender.sendMessage(removed ? "§aHologram §f'" + args[1] + "' §agelöscht!" : "§cHologram nicht gefunden!");
            }
            case "list" -> {
                var all = plugin.getHologramManager().getAll();
                if (all.isEmpty()) {
                    sender.sendMessage("§7Keine Holograms vorhanden.");
                    return true;
                }
                sender.sendMessage("§d§lHolograms:");
                all.forEach((name, entity) -> {
                    String loc = entity == null ? "§cgespawnt" :
                        String.format("§7%.1f, %.1f, %.1f", entity.getLocation().getX(),
                            entity.getLocation().getY(), entity.getLocation().getZ());
                    sender.sendMessage("§f  " + name + " §8– " + loc);
                });
            }
            default -> sender.sendMessage("§cVerwendung: /hologram <create|delete|list>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return filterList(List.of("create", "delete", "list"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            return filterList(new ArrayList<>(plugin.getHologramManager().getAll().keySet()), args[1]);
        }
        return List.of();
    }

    private List<String> filterList(List<String> list, String input) {
        return list.stream().filter(s -> s.toLowerCase().startsWith(input.toLowerCase())).toList();
    }
}
