package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.FriendManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FriendCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public FriendCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        FriendManager fm = plugin.getFriendManager();

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /friend add <Spieler>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(Component.text("§cSpieler ist nicht online!")); return true; }
                if (target.getUniqueId().equals(player.getUniqueId())) { player.sendMessage(Component.text("§cDu kannst dich nicht selbst befreunden!")); return true; }
                if (fm.areFriends(player.getUniqueId(), target.getUniqueId())) {
                    player.sendMessage(Component.text("§cDu bist bereits mit §f" + target.getName() + " §cbefreundet!"));
                    return true;
                }
                if (fm.hasPendingRequest(target.getUniqueId(), player.getUniqueId())) {
                    fm.addFriend(player.getUniqueId(), target.getUniqueId());
                    player.sendMessage(Component.text("§aDu bist jetzt mit §f" + target.getName() + " §abefreundet!"));
                    target.sendMessage(Component.text("§a" + player.getName() + " hat deine Freundschaftsanfrage angenommen!"));
                } else {
                    fm.sendRequest(player.getUniqueId(), target.getUniqueId());
                    player.sendMessage(Component.text("§7Freundschaftsanfrage an §f" + target.getName() + " §7gesendet."));
                    target.sendMessage(Component.text("§e" + player.getName() + " §7möchte dein Freund sein! §e/friend accept " + player.getName() + " §7oder §c/friend deny " + player.getName()));
                }
            }
            case "accept" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /friend accept <Spieler>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(Component.text("§cSpieler ist nicht online!")); return true; }
                if (!fm.hasPendingRequest(target.getUniqueId(), player.getUniqueId())) {
                    player.sendMessage(Component.text("§cKeine Anfrage von §f" + target.getName() + "§c."));
                    return true;
                }
                fm.addFriend(player.getUniqueId(), target.getUniqueId());
                player.sendMessage(Component.text("§aDu bist jetzt mit §f" + target.getName() + " §abefreundet!"));
                target.sendMessage(Component.text("§a" + player.getName() + " hat deine Freundschaftsanfrage angenommen!"));
            }
            case "deny" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /friend deny <Spieler>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(Component.text("§cSpieler ist nicht online!")); return true; }
                if (!fm.hasPendingRequest(target.getUniqueId(), player.getUniqueId())) {
                    player.sendMessage(Component.text("§cKeine Anfrage von §f" + target.getName() + "§c."));
                    return true;
                }
                fm.removeRequest(target.getUniqueId(), player.getUniqueId());
                player.sendMessage(Component.text("§7Anfrage von §f" + target.getName() + " §7abgelehnt."));
                target.sendMessage(Component.text("§c" + player.getName() + " §7hat deine Freundschaftsanfrage abgelehnt."));
            }
            case "remove" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /friend remove <Spieler>")); return true; }
                Player onlineTarget = Bukkit.getPlayer(args[1]);
                OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null || !target.hasPlayedBefore()) { player.sendMessage(Component.text("§cSpieler nicht gefunden!")); return true; }
                if (!fm.areFriends(player.getUniqueId(), target.getUniqueId())) {
                    player.sendMessage(Component.text("§cDieser Spieler ist nicht dein Freund!"));
                    return true;
                }
                fm.removeFriend(player.getUniqueId(), target.getUniqueId());
                player.sendMessage(Component.text("§7" + (target.getName() != null ? target.getName() : args[1]) + " entfernt."));
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) online.sendMessage(Component.text("§7" + player.getName() + " hat dich aus seiner Freundesliste entfernt."));
            }
            case "list" -> {
                Set<UUID> friends = fm.getFriends(player.getUniqueId());
                if (friends.isEmpty()) { player.sendMessage(Component.text("§7Noch keine Freunde. §e/friend add <Spieler>")); return true; }
                player.sendMessage(Component.text("§6§l── Freunde (" + friends.size() + ") ──"));
                for (UUID fUuid : friends) {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer op = Bukkit.getOfflinePlayer(fUuid);
                    String name = op.getName() != null ? op.getName() : fUuid.toString().substring(0, 8);
                    String status = Bukkit.getPlayer(fUuid) != null ? "§a● Online" : "§8● Offline";
                    player.sendMessage(Component.text("  §7" + name + " " + status));
                }
            }
            case "requests" -> {
                List<UUID> incoming = fm.getIncomingRequests(player.getUniqueId());
                if (incoming.isEmpty()) { player.sendMessage(Component.text("§7Keine offenen Anfragen.")); return true; }
                player.sendMessage(Component.text("§6§l── Offene Anfragen ──"));
                for (UUID from : incoming) {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer op = Bukkit.getOfflinePlayer(from);
                    String name = op.getName() != null ? op.getName() : from.toString().substring(0, 8);
                    player.sendMessage(Component.text("  §7" + name + " §8- §e/friend accept " + name + " §7/ §c/friend deny " + name));
                }
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6§l── Freunde ──"));
        player.sendMessage(Component.text("§e/friend add <Spieler> §7- Anfrage senden"));
        player.sendMessage(Component.text("§e/friend accept <Spieler> §7- Anfrage annehmen"));
        player.sendMessage(Component.text("§e/friend deny <Spieler> §7- Anfrage ablehnen"));
        player.sendMessage(Component.text("§e/friend remove <Spieler> §7- Freund entfernen"));
        player.sendMessage(Component.text("§e/friend list §7- Freundesliste"));
        player.sendMessage(Component.text("§e/friend requests §7- Offene Anfragen"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("add", "accept", "deny", "remove", "list", "requests");
        if (args.length == 2 && !List.of("list", "requests").contains(args[0].toLowerCase()))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
