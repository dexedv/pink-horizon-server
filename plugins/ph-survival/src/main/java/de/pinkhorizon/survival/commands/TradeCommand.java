package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.TradeManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public TradeCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur Spieler!"); return true; }

        TradeManager tm = plugin.getTradeManager();

        if (args.length == 0) {
            player.sendMessage(Component.text("§6§l── Handel ──"));
            player.sendMessage(Component.text("§e/trade <Spieler> §7- Handelsanfrage senden"));
            player.sendMessage(Component.text("§e/trade accept <Spieler> §7- Anfrage annehmen"));
            player.sendMessage(Component.text("§e/trade deny <Spieler> §7- Anfrage ablehnen"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /trade accept <Spieler>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(Component.text("§cSpieler nicht online!")); return true; }
                if (!tm.hasRequest(target.getUniqueId(), player.getUniqueId())) {
                    player.sendMessage(Component.text("§cKeine Anfrage von §f" + target.getName() + "§c."));
                    return true;
                }
                if (tm.isInSession(player.getUniqueId()) || tm.isInSession(target.getUniqueId())) {
                    player.sendMessage(Component.text("§cEin Spieler ist bereits im Handel."));
                    return true;
                }
                tm.startSession(target, player);
            }
            case "deny" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /trade deny <Spieler>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(Component.text("§cSpieler nicht online!")); return true; }
                if (!tm.hasRequest(target.getUniqueId(), player.getUniqueId())) {
                    player.sendMessage(Component.text("§cKeine Anfrage von §f" + target.getName() + "§c."));
                    return true;
                }
                tm.cancelRequest(target.getUniqueId());
                player.sendMessage(Component.text("§7Anfrage von §f" + target.getName() + " §7abgelehnt."));
                target.sendMessage(Component.text("§c" + player.getName() + " §7hat deine Handelsanfrage abgelehnt."));
            }
            default -> {
                // /trade <player>
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) { player.sendMessage(Component.text("§cSpieler nicht online!")); return true; }
                if (target.getUniqueId().equals(player.getUniqueId())) { player.sendMessage(Component.text("§cDu kannst nicht mit dir selbst handeln!")); return true; }
                if (tm.isInSession(player.getUniqueId())) { player.sendMessage(Component.text("§cDu bist bereits im Handel!")); return true; }
                if (tm.isInSession(target.getUniqueId())) { player.sendMessage(Component.text("§c" + target.getName() + " ist bereits im Handel.")); return true; }

                // Check if target already sent request to player → auto-accept
                if (tm.hasRequest(target.getUniqueId(), player.getUniqueId())) {
                    tm.startSession(target, player);
                } else {
                    tm.sendRequest(player.getUniqueId(), target.getUniqueId());
                    player.sendMessage(Component.text("§7Handelsanfrage an §f" + target.getName() + " §7gesendet."));
                    target.sendMessage(Component.text("§6" + player.getName() + " §7möchte mit dir handeln! "
                        + "§e/trade accept " + player.getName() + " §7oder §c/trade deny " + player.getName()));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> result = new java.util.ArrayList<>(List.of("accept", "deny"));
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(result::add);
            return result;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
