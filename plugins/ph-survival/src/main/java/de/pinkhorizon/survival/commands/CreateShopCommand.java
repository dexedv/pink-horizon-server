package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreateShopCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public CreateShopCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler!");
            return true;
        }

        if (label.equalsIgnoreCase("removeshop")) {
            plugin.getChestShopListener().startPendingRemove(player);
            return true;
        }

        // /createshop → Spieler in Pending-Modus, wartet auf Truhen-Klick
        plugin.getChestShopListener().addPendingCreate(player.getUniqueId());
        player.sendMessage("§aKlicke auf eine Truhe um sie zum Shop zu machen.");
        player.sendMessage("§7Preise und Menge stellst du danach im GUI ein.");
        return true;
    }
}
