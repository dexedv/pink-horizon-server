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

        if (args.length == 0) {
            sender.sendMessage(c("&eVerwendung: /voteadmin <give|take|set|check|addlink|removelink|listlinks> ..."));
            return true;
        }

        String sub = args[0].toLowerCase();

        // ── Link-Verwaltung ──────────────────────────────────────────────
        if (sub.equals("listlinks")) {
            List<String> links = plugin.getConfig().getStringList("voting-links");
            sender.sendMessage(c("&d&lVoting-Links (" + links.size() + "):"));
            for (int i = 0; i < links.size(); i++)
                sender.sendMessage(c("  &7" + (i + 1) + ". &f" + links.get(i)));
            return true;
        }

        if (sub.equals("addlink")) {
            if (args.length < 2) { sender.sendMessage(c("&eVerwendung: /voteadmin addlink <URL>")); return true; }
            String url = args[1];
            List<String> links = new java.util.ArrayList<>(plugin.getConfig().getStringList("voting-links"));
            links.add(url);
            plugin.getConfig().set("voting-links", links);
            plugin.saveConfig();
            sender.sendMessage(c("&aLink hinzugefügt: &f" + url));
            return true;
        }

        if (sub.equals("removelink")) {
            if (args.length < 2) { sender.sendMessage(c("&eVerwendung: /voteadmin removelink <Nummer>")); return true; }
            List<String> links = new java.util.ArrayList<>(plugin.getConfig().getStringList("voting-links"));
            int idx;
            try { idx = Integer.parseInt(args[1]) - 1; } catch (NumberFormatException e) {
                sender.sendMessage(c("&cUngültige Nummer.")); return true;
            }
            if (idx < 0 || idx >= links.size()) { sender.sendMessage(c("&cNummer außerhalb des Bereichs (1–" + links.size() + ").")); return true; }
            String removed = links.remove(idx);
            plugin.getConfig().set("voting-links", links);
            plugin.saveConfig();
            sender.sendMessage(c("&cLink entfernt: &f" + removed));
            return true;
        }

        // ── Spieler-Verwaltung ───────────────────────────────────────────
        if (args.length < 2) {
            sender.sendMessage(c("&eVerwendung: /voteadmin <give|take|set|check> <Spieler> [Menge]"));
            return true;
        }

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
        if (args.length == 1) return List.of("give", "take", "set", "check", "addlink", "removelink", "listlinks");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }

    private String c(String s) { return s.replace("&", "\u00a7"); }
}
