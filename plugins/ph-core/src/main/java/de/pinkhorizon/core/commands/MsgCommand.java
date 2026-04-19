package de.pinkhorizon.core.commands;

import de.pinkhorizon.core.PHCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MsgCommand implements CommandExecutor {

    private final PHCore plugin;

    public MsgCommand(PHCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("\u00a7cVerwendung: /msg <Spieler> <Nachricht>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("\u00a7cSpieler nicht gefunden!");
            return true;
        }

        String message = String.join(" ", args).substring(args[0].length() + 1);
        String senderName = (sender instanceof Player p) ? p.getName() : "Konsole";

        sender.sendMessage("\u00a77[Du -> " + target.getName() + "] \u00a7f" + message);
        target.sendMessage("\u00a77[" + senderName + " -> Dir] \u00a7f" + message);
        return true;
    }
}
