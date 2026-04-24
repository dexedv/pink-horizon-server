package de.pinkhorizon.vote.commands;

import de.pinkhorizon.vote.PHVote;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VoteCoinsCommand implements CommandExecutor {

    private final PHVote plugin;

    public VoteCoinsCommand(PHVote plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler.");
            return true;
        }

        int coins = plugin.getVoteCoinManager().getCoins(player.getUniqueId());
        int total = plugin.getVoteCoinManager().getTotalVotes(player.getUniqueId());

        player.sendMessage(c("&d&l[VoteCoins] &7Du hast &d&l" + coins + " VoteCoin(s)&7."));
        player.sendMessage(c("&8Votes gesamt: &7" + total + " &8| &7/voteshop &8| &7/vote"));
        return true;
    }

    private String c(String s) { return s.replace("&", "\u00a7"); }
}
