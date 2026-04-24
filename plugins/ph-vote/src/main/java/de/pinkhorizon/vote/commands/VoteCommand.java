package de.pinkhorizon.vote.commands;

import de.pinkhorizon.vote.PHVote;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class VoteCommand implements CommandExecutor {

    private final PHVote plugin;

    public VoteCommand(PHVote plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> links = plugin.getConfig().getStringList("voting-links");
        int coins = 0;

        if (sender instanceof Player player) {
            coins = plugin.getVoteCoinManager().getCoins(player.getUniqueId());
            int total = plugin.getVoteCoinManager().getTotalVotes(player.getUniqueId());
            sender.sendMessage(c(""));
            sender.sendMessage(c("&d&l━━━ Pink Horizon · Voting ━━━"));
            sender.sendMessage(c("&7Deine VoteCoins: &d&l" + coins));
            sender.sendMessage(c("&7Votes gesamt:   &f" + total));
        } else {
            sender.sendMessage(c("&d&l━━━ Pink Horizon · Voting ━━━"));
        }

        sender.sendMessage(c(""));
        sender.sendMessage(c("&7Stimme auf diesen Seiten ab:"));
        for (int i = 0; i < links.size(); i++) {
            sender.sendMessage(c("  &d" + (i + 1) + ". &f" + links.get(i)));
        }
        sender.sendMessage(c(""));
        sender.sendMessage(c("&7Pro Vote: &d+1 VoteCoin &7→ &f/voteshop"));
        sender.sendMessage(c("&d&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(c(""));

        return true;
    }

    private String c(String s) { return s.replace("&", "\u00a7"); }
}
