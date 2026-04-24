package de.pinkhorizon.vote.commands;

import de.pinkhorizon.vote.PHVote;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class VoteAdminCommand implements CommandExecutor, TabCompleter {

    private final PHVote plugin;

    public VoteAdminCommand(PHVote plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vote.admin")) {
            sender.sendMessage(c("&cKeine Berechtigung."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(c("&eVerwendung: /voteadmin <give|take|set|check> <Spieler> [Menge]"));
            return true;
        }

        String sub    = args[0].toLowerCase();
        String target = args[1];

        // Spieler online suchen
        Player player = Bukkit.getPlayerExact(target);

        if (sub.equals("check")) {
            if (player == null) { sender.sendMessage(c("&cSpieler nicht online.")); return true; }
            int coins = plugin.getVoteCoinManager().getCoins(player.getUniqueId());
            int total = plugin.getVoteCoinManager().getTotalVotes(player.getUniqueId());
            sender.sendMessage(c("&7" + player.getName() + ": &d" + coins
                + " VoteCoins &8| &7" + total + " Votes gesamt"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(c("&eVerwendung: /voteadmin " + sub + " <Spieler> <Menge>"));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(c("&cUngültige Zahl: " + args[2]));
            return true;
        }

        if (player == null) { sender.sendMessage(c("&cSpieler nicht online.")); return true; }

        switch (sub) {
            case "give" -> {
                plugin.getVoteCoinManager().addCoins(player.getUniqueId(), player.getName(), amount, false);
                sender.sendMessage(c("&a+" + amount + " VoteCoins → " + player.getName()));
                player.sendMessage(c("&d[VoteAdmin] &7Du hast &d+" + amount + " VoteCoin(s) &7erhalten."));
            }
            case "take" -> {
                boolean ok = plugin.getVoteCoinManager().removeCoins(player.getUniqueId(), amount);
                if (ok) {
                    sender.sendMessage(c("&c-" + amount + " VoteCoins → " + player.getName()));
                    player.sendMessage(c("&d[VoteAdmin] &7Dir wurden &c-" + amount + " VoteCoin(s) &7abgezogen."));
                } else {
                    sender.sendMessage(c("&cNicht genug VoteCoins bei " + player.getName() + "."));
                }
            }
            case "set" -> {
                plugin.getVoteCoinManager().setCoins(player.getUniqueId(), player.getName(), amount);
                sender.sendMessage(c("&7" + player.getName() + " → &d" + amount + " VoteCoins gesetzt."));
                player.sendMessage(c("&d[VoteAdmin] &7Deine VoteCoins wurden auf &d" + amount + " &7gesetzt."));
            }
            default -> sender.sendMessage(c("&eUnbekannte Aktion: " + sub));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("give", "take", "set", "check");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }

    private String c(String s) { return s.replace("&", "\u00a7"); }
}
