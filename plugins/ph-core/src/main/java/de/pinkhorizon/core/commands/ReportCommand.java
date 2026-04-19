package de.pinkhorizon.core.commands;

import de.pinkhorizon.core.PHCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReportCommand implements CommandExecutor {

    private final PHCore plugin;

    public ReportCommand(PHCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player reporter)) {
            sender.sendMessage("Nur Spieler können reporten.");
            return true;
        }

        if (args.length < 2) {
            reporter.sendMessage("\u00a7cVerwendung: /report <Spieler> <Grund>");
            return true;
        }

        String targetName = args[0];
        String reason = String.join(" ", args).substring(targetName.length() + 1);

        // Admins benachrichtigen
        String msg = "\u00a7c[Report] \u00a7f" + reporter.getName() + " hat " + targetName
                + " gemeldet: \u00a7e" + reason;

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("pinkhorizon.admin"))
                .forEach(admin -> admin.sendMessage(msg));

        plugin.getLogger().info(msg);
        reporter.sendMessage("\u00a7aDein Report wurde abgeschickt. Danke!");
        return true;
    }
}
