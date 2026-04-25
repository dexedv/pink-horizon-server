package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.gui.AdminPanelGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminPanelCommand implements CommandExecutor {

    private final AdminPanelGui gui;

    public AdminPanelCommand(AdminPanelGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler.");
            return true;
        }
        if (!player.hasPermission("survival.admin")) {
            player.sendMessage("§cKein Zugriff.");
            return true;
        }
        gui.openMain(player);
        return true;
    }
}
