package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class HomeAddCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public HomeAddCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survival.admin")) {
            sender.sendMessage("\u00a7cKeine Berechtigung!");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("\u00a7cVerwendung: /homeadd <Spieler> <Anzahl>");
            return true;
        }

        Player online = Bukkit.getPlayer(args[0]);
        UUID targetUuid;
        String targetName;

        if (online != null) {
            targetUuid = online.getUniqueId();
            targetName = online.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage("\u00a7cSpieler nicht gefunden!");
                return true;
            }
            targetUuid = offline.getUniqueId();
            targetName = offline.getName() != null ? offline.getName() : args[0];
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("\u00a7cUng\u00fcltige Anzahl!");
            return true;
        }

        for (int i = 0; i < amount; i++) {
            plugin.getUpgradeManager().addExtraHome(targetUuid);
        }

        int total = plugin.getRankManager().getMaxHomes(targetUuid);
        sender.sendMessage("\u00a7a" + amount + " Extra-Home(s) an \u00a7f" + targetName
                + "\u00a7a vergeben. Gesamt-Limit: \u00a7f" + total);

        if (online != null) {
            online.sendMessage("\u00a7aDu hast \u00a7f" + amount
                    + "\u00a7a zus\u00e4tzliche(n) Home-Slot(s) erhalten! Gesamt: \u00a7f" + total);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
