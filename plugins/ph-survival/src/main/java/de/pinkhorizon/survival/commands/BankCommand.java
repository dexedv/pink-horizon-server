package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class BankCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public BankCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur Spieler!"); return true; }

        var bm = plugin.getBankManager();
        var em = plugin.getEconomyManager();

        if (args.length == 0) {
            player.sendMessage(Component.text("§6§l── Bank ──"));
            player.sendMessage(Component.text("§7Wallet: §e" + em.getBalance(player.getUniqueId()) + " Coins"));
            player.sendMessage(Component.text("§7Bank:   §e" + bm.getBalance(player.getUniqueId()) + " Coins"));
            player.sendMessage(Component.text("§8Zinsen: §72% täglich auf max. 100.000 Coins"));
            player.sendMessage(Component.text("§e/bank deposit <Betrag> §8| §e/bank withdraw <Betrag>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "deposit", "einzahlen" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /bank deposit <Betrag>")); return true; }
                long amount = parseAmount(player, args[1]);
                if (amount <= 0) return true;
                if (!bm.deposit(player.getUniqueId(), amount)) {
                    player.sendMessage(Component.text("§cNicht genug Coins! (Wallet: §f" + em.getBalance(player.getUniqueId()) + "§c)"));
                } else {
                    player.sendMessage(Component.text("§a" + amount + " Coins eingezahlt. Bankguthaben: §e" + bm.getBalance(player.getUniqueId())));
                    plugin.getAchievementManager().checkBank(player);
                }
            }
            case "withdraw", "auszahlen" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /bank withdraw <Betrag>")); return true; }
                long amount = parseAmount(player, args[1]);
                if (amount <= 0) return true;
                if (!bm.withdraw(player.getUniqueId(), amount)) {
                    player.sendMessage(Component.text("§cNicht genug auf der Bank! (Bank: §f" + bm.getBalance(player.getUniqueId()) + "§c)"));
                } else {
                    player.sendMessage(Component.text("§a" + amount + " Coins abgehoben. Wallet: §e" + em.getBalance(player.getUniqueId())));
                }
            }
            default -> player.sendMessage(Component.text("§cUnbekannter Unterbefehl. Nutze: §e/bank §8| §e/bank deposit §8| §e/bank withdraw"));
        }
        return true;
    }

    private long parseAmount(Player player, String s) {
        try {
            long v = Long.parseLong(s);
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("§cUngültiger Betrag!"));
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("deposit", "withdraw");
        return List.of();
    }
}
