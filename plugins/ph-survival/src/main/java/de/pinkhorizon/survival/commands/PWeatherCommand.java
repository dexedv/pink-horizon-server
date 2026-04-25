package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.WeatherType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

public class PWeatherCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> ALLOWED_RANKS = Set.of("siedler", "krieger", "legende");

    private final PHSurvival plugin;

    public PWeatherCommand(PHSurvival plugin) {
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
            p.sendMessage("§cDu kannst das Wetter nur in deinen eigenen Claims ändern.");
            return true;
        }

        if (args.length == 0) {
            p.sendMessage("§eVerwendung: §f/pweather <clear|rain|reset>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "clear" -> { p.setPlayerWeather(WeatherType.CLEAR); p.sendMessage("§7Wetter gesetzt: §eSonnig§7."); }
            case "rain"  -> { p.setPlayerWeather(WeatherType.DOWNFALL); p.sendMessage("§7Wetter gesetzt: §9Regen§7."); }
            case "reset" -> { p.resetPlayerWeather(); p.sendMessage("§7Dein Wetter wurde §czurückgesetzt§7."); }
            default      -> p.sendMessage("§eVerwendung: §f/pweather <clear|rain|reset>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("clear", "rain", "reset").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
