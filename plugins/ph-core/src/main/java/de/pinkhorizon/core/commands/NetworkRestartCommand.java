package de.pinkhorizon.core.commands;

import de.pinkhorizon.core.PHCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * /networkrestart <minuten>
 * Wird vom Dashboard per RCON aufgerufen.
 * Berechtigung: pinkhorizon.admin (OPs können ihn auch aus der Konsole nutzen).
 */
public class NetworkRestartCommand implements CommandExecutor, TabCompleter {

    private final PHCore plugin;

    public NetworkRestartCommand(PHCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pinkhorizon.admin") && !sender.getName().equals("CONSOLE")) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }

        int minutes = 5;
        if (args.length >= 1) {
            try { minutes = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) {
                sender.sendMessage("§cVerwendung: /networkrestart <minuten>");
                return true;
            }
        }

        if (minutes < 1 || minutes > 60) {
            sender.sendMessage("§cMinuten müssen zwischen 1 und 60 liegen.");
            return true;
        }

        plugin.getNetworkRestartManager().triggerManual(minutes * 60);
        sender.sendMessage("§a✔ Netzwerk-Neustart in §e" + minutes + " Minute"
            + (minutes > 1 ? "n" : "") + " §aangesetzt.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("1", "2", "3", "5", "10");
        return List.of();
    }
}
