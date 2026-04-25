package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SurvivalRankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * /rankset <Spieler> <Rang>
 * Setzt den Survival-Rang eines Spielers (online & offline).
 * Wird von Tebex nach einem Kauf aufgerufen.
 */
public class SurvivalRankSetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SHOP_RANKS = List.of("siedler", "krieger", "legende");

    private final PHSurvival plugin;

    public SurvivalRankSetCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survival.admin")) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§eVerwendung: /rankset <Spieler> <Rang>");
            sender.sendMessage("§eRänge: " + String.join(", ", SHOP_RANKS));
            return true;
        }

        String rankId = args[1].toLowerCase();
        if (!SurvivalRankManager.RANKS.containsKey(rankId)) {
            sender.sendMessage("§cUnbekannter Rang: §e" + rankId);
            sender.sendMessage("§7Gültig: " + String.join(", ", SHOP_RANKS));
            return true;
        }

        // Online?
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            plugin.getRankManager().setRank(online.getUniqueId(), online.getName(), rankId);
            SurvivalRankManager.Rank r = SurvivalRankManager.RANKS.get(rankId);
            sender.sendMessage("§aRang gesetzt: §f" + online.getName()
                    + " §8→ " + r.chatPrefix.replace("§", "§") + " §7(Claims: " + r.maxClaims + ")");
            online.sendMessage("§dDein Rang wurde auf §r" + r.chatPrefix + "§dgesetzt! Claims: §f" + r.maxClaims);
            return true;
        }

        // Offline
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
        UUID uuid = offline.getUniqueId();
        String name = offline.getName() != null ? offline.getName() : args[0];

        plugin.getRankManager().setRank(uuid, name, rankId);
        SurvivalRankManager.Rank r = SurvivalRankManager.RANKS.get(rankId);
        sender.sendMessage("§aRang (offline) gesetzt: §f" + name
                + " §8→ " + r.chatPrefix + " §7(Claims: " + r.maxClaims + ")");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 2) return SHOP_RANKS;
        return List.of();
    }
}
