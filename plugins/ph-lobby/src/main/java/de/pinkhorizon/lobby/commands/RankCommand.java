package de.pinkhorizon.lobby.commands;

import de.pinkhorizon.lobby.PHLobby;
import de.pinkhorizon.lobby.managers.RankManager;
import de.pinkhorizon.lobby.managers.RankManager.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final PHLobby plugin;

    public RankCommand(PHLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lobby.admin")) {
            sender.sendMessage("§cKeine Berechtigung!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(prefix() + "§cNutzung: /rank set <Spieler> <Rang>");
                    return true;
                }
                setRank(sender, args[1], args[2].toLowerCase());
            }
            case "info" -> {
                String target = args.length >= 2 ? args[1]
                    : (sender instanceof Player p ? p.getName() : null);
                if (target == null) {
                    sender.sendMessage(prefix() + "§cNutzung: /rank info <Spieler>");
                    return true;
                }
                showInfo(sender, target);
            }
            case "list" -> showList(sender);
            default    -> sendHelp(sender);
        }
        return true;
    }

    // ── Rang setzen ──────────────────────────────────────────────────────

    private void setRank(CommandSender sender, String targetName, String rankId) {
        RankManager rm = plugin.getRankManager();

        if (!RankManager.RANKS.containsKey(rankId)) {
            sender.sendMessage(prefix() + "§cUnbekannter Rang: §f" + rankId
                + "§c. Verfügbar: §f" + String.join(", ", RankManager.RANKS.keySet()));
            return;
        }

        // Spieler online? → direkt updaten
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) {
            rm.setRank(target.getUniqueId(), target.getName(), rankId);
            rm.applyTabName(target);
            target.sendMessage(prefix() + "§7Dein Rang wurde auf §f"
                + RankManager.RANKS.get(rankId).chatPrefix + rankId + " §7gesetzt.");
        } else {
            // Offline: per Name im Cache nachschlagen
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(prefix() + "§cSpieler §f" + targetName + " §cnicht gefunden.");
                return;
            }
            rm.setRank(offline.getUniqueId(), offline.getName(), rankId);
        }

        Rank rank = RankManager.RANKS.get(rankId);
        sender.sendMessage(prefix() + "§7Rang von §f" + targetName
            + " §7→ " + rank.chatPrefix + rankId);
    }

    // ── Rang-Info ────────────────────────────────────────────────────────

    private void showInfo(CommandSender sender, String targetName) {
        RankManager rm = plugin.getRankManager();
        Player online = Bukkit.getPlayerExact(targetName);

        if (online != null) {
            Rank rank = rm.getRank(online.getUniqueId());
            sender.sendMessage(prefix() + "§f" + online.getName()
                + " §7hat den Rang: " + rank.chatPrefix + rank.id);
        } else {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(prefix() + "§cSpieler nicht gefunden.");
                return;
            }
            Rank rank = rm.getRank(offline.getUniqueId());
            sender.sendMessage(prefix() + "§f" + targetName
                + " §7(offline) hat den Rang: " + rank.chatPrefix + rank.id);
        }
    }

    // ── Rang-Liste ───────────────────────────────────────────────────────

    private void showList(CommandSender sender) {
        sender.sendMessage(Component.text("══════ Verfügbare Ränge ══════", TextColor.color(0xFF69B4)));
        RankManager.RANKS.forEach((id, rank) ->
            sender.sendMessage(Component.text("  " + rank.chatPrefix + id, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("══════════════════════════════", TextColor.color(0xFF69B4)));
    }

    // ── Hilfe ────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("══════ /rank ══════", TextColor.color(0xFF69B4)));
        sender.sendMessage(Component.text("  /rank set <Spieler> <Rang>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /rank info [Spieler]", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /rank list", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("══════════════════════", TextColor.color(0xFF69B4)));
    }

    private String prefix() { return "§5[Rang] §r"; }

    // ── Tab-Completion ───────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lobby.admin")) return List.of();

        if (args.length == 1) return List.of("set", "info", "list");

        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("info"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return names;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return new ArrayList<>(RankManager.RANKS.keySet());
        }

        return List.of();
    }
}
