package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public BalanceCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }
        long bal = plugin.getEconomyManager().getBalance(player.getUniqueId());
        player.sendMessage("\u00a7dDein Kontostand: \u00a7f" + bal + " \u00a7dCoins");
        return true;
    }
}
