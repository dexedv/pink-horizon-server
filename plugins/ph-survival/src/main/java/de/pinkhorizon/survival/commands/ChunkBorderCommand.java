package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChunkBorderCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public ChunkBorderCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }
        boolean nowVisible = plugin.getClaimBorderVisualizer().toggle(player.getUniqueId());
        if (nowVisible) {
            player.sendMessage("§aChunk-Grenzen §laktiviert§r§a.");
        } else {
            player.sendMessage("§7Chunk-Grenzen §lausgeblendet§r§7.");
        }
        return true;
    }
}
