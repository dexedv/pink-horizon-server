package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /phinfo <name>
 * Gibt maschinenlesbare Spielerdaten für das Dashboard aus.
 * Erfordert survival.admin (RCON hat automatisch alle Rechte).
 */
public class PlayerInfoCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public PlayerInfoCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survival.admin")) {
            sender.sendMessage("NOT_AUTHORIZED");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("USAGE:/phinfo <name>");
            return true;
        }

        Player online = Bukkit.getPlayer(args[0]);
        OfflinePlayer op = online != null ? online : Bukkit.getOfflinePlayer(args[0]);
        if (!op.hasPlayedBefore()) {
            sender.sendMessage("NOT_FOUND");
            return true;
        }

        UUID   uuid = op.getUniqueId();
        String name = op.getName() != null ? op.getName() : args[0];

        long   coins        = plugin.getEconomyManager().getBalance(uuid);
        String rank         = plugin.getRankManager().getRank(uuid).id;
        long   playtime     = plugin.getStatsManager().getPlaytime(uuid);
        int    deaths       = plugin.getStatsManager().getDeaths(uuid);
        int    mobKills     = plugin.getStatsManager().getMobKills(uuid);
        int    playerKills  = plugin.getStatsManager().getPlayerKills(uuid);
        int    blocksBroken = plugin.getStatsManager().getBlocksBroken(uuid);

        JobManager     jm  = plugin.getJobManager();
        JobManager.Job job = jm.getJob(uuid);
        int  jobLevel = job != null ? jm.getLevelForJob(uuid, job) : 0;
        int  jobXp    = job != null ? jm.getXp(uuid) : 0;

        sender.sendMessage("NAME:" + name);
        sender.sendMessage("COINS:" + coins);
        sender.sendMessage("RANK:" + rank);
        sender.sendMessage("PLAYTIME:" + playtime);
        sender.sendMessage("DEATHS:" + deaths);
        sender.sendMessage("MOB_KILLS:" + mobKills);
        sender.sendMessage("PLAYER_KILLS:" + playerKills);
        sender.sendMessage("BLOCKS_BROKEN:" + blocksBroken);
        sender.sendMessage("JOB:" + (job != null ? job.name() : "NONE"));
        sender.sendMessage("JOB_DISPLAY:" + (job != null ? job.displayName : "-"));
        sender.sendMessage("JOB_LEVEL:" + jobLevel);
        sender.sendMessage("JOB_XP:" + jobXp);
        return true;
    }
}
