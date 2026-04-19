package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EcoCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public EcoCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survival.admin")) {
            sender.sendMessage("\u00a7cKeine Berechtigung!");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("\u00a7cVerwendung: /eco <give|take|set|check> <Spieler> [Betrag]");
            return true;
        }

        String subCmd = args[0].toLowerCase();
        Player target = Bukkit.getPlayer(args[1]);
        UUID targetUuid = null;
        String targetName = args[1];

        if (target != null) {
            targetUuid = target.getUniqueId();
        } else {
            // Try offline player
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
            if (offline.hasPlayedBefore()) {
                targetUuid = offline.getUniqueId();
                targetName = offline.getName() != null ? offline.getName() : args[1];
            }
        }

        if (targetUuid == null) {
            sender.sendMessage("\u00a7cSpieler nicht gefunden!");
            return true;
        }

        if (subCmd.equals("check")) {
            long bal = plugin.getEconomyManager().getBalance(targetUuid);
            sender.sendMessage("\u00a7f" + targetName + "\u00a77 hat \u00a7f" + bal + "\u00a77 Coins.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("\u00a7cVerwendung: /eco " + subCmd + " <Spieler> <Betrag>");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("\u00a7cUng\u00fcltiger Betrag!");
            return true;
        }

        switch (subCmd) {
            case "give" -> {
                plugin.getEconomyManager().deposit(targetUuid, amount);
                sender.sendMessage("\u00a7a" + amount + " Coins zu \u00a7f" + targetName + "\u00a7a hinzugef\u00fcgt.");
                if (target != null) target.sendMessage("\u00a7aDu hast \u00a7f" + amount + "\u00a7a Coins erhalten.");
            }
            case "take" -> {
                if (!plugin.getEconomyManager().withdraw(targetUuid, amount)) {
                    sender.sendMessage("\u00a7c" + targetName + " hat nicht genug Coins!");
                } else {
                    sender.sendMessage("\u00a7c" + amount + " Coins von \u00a7f" + targetName + "\u00a7c abgezogen.");
                    if (target != null) target.sendMessage("\u00a7c" + amount + " Coins wurden von deinem Konto abgezogen.");
                }
            }
            case "set" -> {
                plugin.getEconomyManager().setBalance(targetUuid, amount);
                sender.sendMessage("\u00a7aKontostand von \u00a7f" + targetName + "\u00a7a auf \u00a7f" + amount + "\u00a7a gesetzt.");
                if (target != null) target.sendMessage("\u00a7aDein Kontostand wurde auf \u00a7f" + amount + "\u00a7a Coins gesetzt.");
            }
            default -> sender.sendMessage("\u00a7cUnbekannter Sub-Befehl! Nutze give, take, set oder check.");
        }
        return true;
    }
}
