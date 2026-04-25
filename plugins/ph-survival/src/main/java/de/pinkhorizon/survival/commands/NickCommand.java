package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SurvivalRankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

public class NickCommand implements CommandExecutor {

    private static final Set<String> ALLOWED_RANKS = Set.of("siedler", "krieger", "legende");
    private static final int MAX_LENGTH = 20;

    private final PHSurvival plugin;

    public NickCommand(PHSurvival plugin) {
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

        if (args.length == 0) {
            p.sendMessage("§eVerwendung: §f/nick <Name> §8| §f/nick reset");
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            p.displayName(Component.text(p.getName()));
            p.playerListName(Component.text(p.getName()));
            p.sendMessage("§7Dein Nickname wurde §czurückgesetzt§7.");
            return true;
        }

        String raw = String.join(" ", args);

        // Strip color codes for length check
        String plain = raw.replaceAll("&[0-9a-fk-or]", "");
        if (plain.length() > MAX_LENGTH) {
            p.sendMessage("§cNickname zu lang! Maximal " + MAX_LENGTH + " Zeichen.");
            return true;
        }
        if (plain.isBlank()) {
            p.sendMessage("§cUngültiger Nickname.");
            return true;
        }

        Component nick = LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
        p.displayName(nick);
        p.playerListName(nick);
        p.sendMessage(Component.text("§7Nickname gesetzt: ").append(nick));
        return true;
    }
}
