package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.gui.SpawnerShopGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnerShopCommand implements CommandExecutor {

    private final SpawnerShopGui gui;

    public SpawnerShopCommand(SpawnerShopGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler.");
            return true;
        }
        gui.open(player, 0);
        return true;
    }
}
