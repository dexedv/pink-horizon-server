package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.gui.TutorialGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TutorialCommand implements CommandExecutor {

    private final TutorialGui gui;

    public TutorialCommand(TutorialGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler!");
            return true;
        }
        gui.openMain(player);
        return true;
    }
}
