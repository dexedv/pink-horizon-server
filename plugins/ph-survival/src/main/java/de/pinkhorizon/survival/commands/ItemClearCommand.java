package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ItemClearCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public ItemClearCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survival.admin")) {
            sender.sendMessage("§cDu hast keine Berechtigung für diesen Befehl.");
            return true;
        }
        plugin.getItemClearManager().executeClear(sender);
        return true;
    }
}
