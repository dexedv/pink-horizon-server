package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

public class PTimeCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> ALLOWED_RANKS = Set.of("siedler", "krieger", "legende");

    private final PHSurvival plugin;

    public PTimeCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cNur für Spieler.");
            return true;
        }

        String rankId = plugin.getRankManager().getRank(p.getUniqueId()).id;
        if (!ALLOWED_RANKS.contains(rankId) && !p.hasPermission("survival.admin")) {
            p.sendMessage("§cDu benötigst mindestens den §a[Siedler]§c-Rang für diesen Command.");
            return true;
        }

        if (!plugin.getClaimManager().isOwner(p.getLocation().getChunk(), p.getUniqueId())
                && !p.hasPermission("survival.admin")) {
            p.sendMessage("§cDu kannst die Zeit nur in deinen eigenen Claims ändern.");
            return true;
        }

        if (args.length == 0) {
            p.sendMessage("§eVerwendung: §f/ptime <day|night|dawn|dusk|reset>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "day"   -> { p.setPlayerTime(6000L,  false); p.sendMessage("§7Zeit gesetzt: §eTag§7."); }
            case "night" -> { p.setPlayerTime(18000L, false); p.sendMessage("§7Zeit gesetzt: §8Nacht§7."); }
            case "dawn"  -> { p.setPlayerTime(23000L, false); p.sendMessage("§7Zeit gesetzt: §6Morgengrauen§7."); }
            case "dusk"  -> { p.setPlayerTime(13000L, false); p.sendMessage("§7Zeit gesetzt: §5Dämmerung§7."); }
            case "reset" -> { p.resetPlayerTime();            p.sendMessage("§7Deine Zeit wurde §czurückgesetzt§7."); }
            default      -> p.sendMessage("§eVerwendung: §f/ptime <day|night|dawn|dusk|reset>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("day", "night", "dawn", "dusk", "reset").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
