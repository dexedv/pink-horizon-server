package de.pinkhorizon.core.commands;

import de.pinkhorizon.core.PHCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HubCommand implements CommandExecutor {

    private final PHCore plugin;

    public HubCommand(PHCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        // Teleportiert zum Spawn / Hub (Velocity-Nachricht zum Hub-Server wird hier hinzugefügt)
        player.sendMessage("\u00a7dDu wirst zum Hub teleportiert...");
        player.performCommand("spawn");
        return true;
    }
}
