package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.AchievementManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

public class AchievementCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public AchievementCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur Spieler!"); return true; }

        AchievementManager am = plugin.getAchievementManager();
        Set<AchievementManager.Achievement> unlocked = am.getUnlocked(player.getUniqueId());
        AchievementManager.Achievement[] all = AchievementManager.Achievement.values();

        player.sendMessage(Component.text("§6§l── Achievements §7(" + unlocked.size() + "/" + all.length + ") ──"));
        for (AchievementManager.Achievement a : all) {
            boolean done = unlocked.contains(a);
            player.sendMessage(Component.text(
                (done ? "§a✔ §f" : "§8✘ §7") + a.displayName + " §8" + a.stars
                + " §8- §7" + a.description
                + (done ? "" : " §8(+" + a.reward + " Coins)")
            ));
        }
        return true;
    }
}
