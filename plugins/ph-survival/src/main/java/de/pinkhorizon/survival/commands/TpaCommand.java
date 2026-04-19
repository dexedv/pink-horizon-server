package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TpaCommand implements CommandExecutor {

    private final PHSurvival plugin;
    // Ziel-UUID -> Anfragender-UUID
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    // Ziel-UUID -> Ablauf-Task
    private final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();

    public TpaCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cNur Spieler!");
            return true;
        }

        switch (label.toLowerCase()) {
            case "tpa" -> {
                if (args.length < 1) {
                    player.sendMessage("\u00a7cVerwendung: /tpa <Spieler>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage("\u00a7cSpieler nicht gefunden!");
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage("\u00a7cDu kannst keine Anfrage an dich selbst senden!");
                    return true;
                }
                // Cancel previous expiry if exists
                BukkitTask oldTask = expiryTasks.remove(target.getUniqueId());
                if (oldTask != null) oldTask.cancel();

                pendingRequests.put(target.getUniqueId(), player.getUniqueId());
                BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    pendingRequests.remove(target.getUniqueId());
                    expiryTasks.remove(target.getUniqueId());
                    player.sendMessage("\u00a77Deine TPA-Anfrage an \u00a7f" + target.getName() + "\u00a77 ist abgelaufen.");
                    target.sendMessage("\u00a77Die TPA-Anfrage von \u00a7f" + player.getName() + "\u00a77 ist abgelaufen.");
                }, 20L * 60);
                expiryTasks.put(target.getUniqueId(), task);

                player.sendMessage("\u00a7aTPA-Anfrage an \u00a7f" + target.getName() + "\u00a7a gesendet. L\u00e4uft in 60 Sekunden ab.");
                target.sendMessage("\u00a7f" + player.getName() + "\u00a7a m\u00f6chte zu dir teleportiert werden! Tippe \u00a7f/tpaccept\u00a7a oder \u00a7f/tpdeny\u00a7a.");
            }
            case "tpaccept" -> {
                UUID requesterId = pendingRequests.remove(player.getUniqueId());
                BukkitTask task = expiryTasks.remove(player.getUniqueId());
                if (task != null) task.cancel();
                if (requesterId == null) {
                    player.sendMessage("\u00a7cKeine offene TPA-Anfrage.");
                    return true;
                }
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester == null || !requester.isOnline()) {
                    player.sendMessage("\u00a7cDer Spieler ist nicht mehr online.");
                    return true;
                }
                requester.teleport(player.getLocation());
                player.sendMessage("\u00a7aTPA-Anfrage akzeptiert. \u00a7f" + requester.getName() + "\u00a7a wurde zu dir teleportiert.");
                requester.sendMessage("\u00a7aDu wurdest zu \u00a7f" + player.getName() + "\u00a7a teleportiert!");
            }
            case "tpdeny" -> {
                UUID requesterId = pendingRequests.remove(player.getUniqueId());
                BukkitTask task = expiryTasks.remove(player.getUniqueId());
                if (task != null) task.cancel();
                if (requesterId == null) {
                    player.sendMessage("\u00a7cKeine offene TPA-Anfrage.");
                    return true;
                }
                Player requester = Bukkit.getPlayer(requesterId);
                player.sendMessage("\u00a7aTPA-Anfrage abgelehnt.");
                if (requester != null && requester.isOnline()) {
                    requester.sendMessage("\u00a7c" + player.getName() + " hat deine TPA-Anfrage abgelehnt.");
                }
            }
        }
        return true;
    }
}
