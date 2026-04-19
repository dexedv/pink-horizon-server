package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public PayCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }
        if (args.length < 2) {
            payer.sendMessage("\u00a7cVerwendung: /pay <Spieler> <Betrag>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            payer.sendMessage("\u00a7cSpieler nicht gefunden!");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            payer.sendMessage("\u00a7cUngültiger Betrag!");
            return true;
        }

        if (!plugin.getEconomyManager().withdraw(payer.getUniqueId(), amount)) {
            payer.sendMessage("\u00a7cNicht genug Coins!");
            return true;
        }

        plugin.getEconomyManager().deposit(target.getUniqueId(), amount);
        payer.sendMessage("\u00a7aDu hast " + amount + " Coins an " + target.getName() + " ueberwiesen.");
        target.sendMessage("\u00a7aDu hast " + amount + " Coins von " + payer.getName() + " erhalten.");
        return true;
    }
}
