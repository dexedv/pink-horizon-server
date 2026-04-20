package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.QuestManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class QuestCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public QuestCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur Spieler!"); return true; }

        List<QuestManager.Quest> quests = plugin.getQuestManager().getDailyQuests(player.getUniqueId());
        player.sendMessage(Component.text("§6§l── Tägliche Quests ──"));
        for (QuestManager.Quest quest : quests) {
            if (quest.completed()) {
                player.sendMessage(Component.text("§a✔ §f" + quest.type().description + " §a§lFERTIG §8(+" + 300 + " Coins)"));
            } else {
                int pct = (int) ((double) quest.progress() / quest.type().goal * 100);
                player.sendMessage(Component.text("§e» §f" + quest.type().description
                    + " §7(" + quest.progress() + "§8/§7" + quest.type().goal + " §8- §7" + pct + "%)"));
            }
        }
        player.sendMessage(Component.text("§8Belohnung: §6300 Coins §8pro Quest · Setzt täglich zurück"));
        return true;
    }
}
