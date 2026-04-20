package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.MailManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class MailCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;

    public MailCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "send" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("§cNutzung: /mail send <Spieler> <Nachricht>"));
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (!target.hasPlayedBefore() && Bukkit.getPlayer(args[1]) == null) {
                    player.sendMessage(Component.text("§cSpieler nicht gefunden!"));
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(Component.text("§cDu kannst dir selbst keine Nachricht schicken!"));
                    return true;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (!plugin.getMailManager().send(player.getName(), target.getUniqueId(), message)) {
                    player.sendMessage(Component.text("§cPostfach von §f" + target.getName() + " §cist voll! (Max. 50)"));
                } else {
                    player.sendMessage(Component.text("§aNachricht an §f" + target.getName() + " §agesendet!"));
                    Player online = Bukkit.getPlayer(target.getUniqueId());
                    if (online != null) {
                        online.sendMessage(Component.text("§e✉ §7Neue Nachricht von §f" + player.getName() + "§7! §e/mail read"));
                    }
                }
            }
            case "read" -> {
                MailManager mm = plugin.getMailManager();
                List<MailManager.Mail> mails = mm.getMails(player.getUniqueId());
                if (mails.isEmpty()) {
                    player.sendMessage(Component.text("§7Du hast keine Nachrichten."));
                    return true;
                }
                player.sendMessage(Component.text("§6§l── Nachrichten (" + mails.size() + ") ──"));
                for (int i = 0; i < mails.size(); i++) {
                    MailManager.Mail m = mails.get(i);
                    String col = m.read() ? "§8" : "§e";
                    player.sendMessage(Component.text(col + "[" + (i + 1) + "] §f" + m.sender()
                        + " §8(" + m.timestamp() + "): §7" + m.message()));
                }
                mm.markAllRead(player.getUniqueId());
            }
            case "clear" -> {
                plugin.getMailManager().clearMails(player.getUniqueId());
                player.sendMessage(Component.text("§7Postfach geleert."));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6§l── Mail ──"));
        player.sendMessage(Component.text("§e/mail send <Spieler> <Nachricht> §7- Nachricht senden"));
        player.sendMessage(Component.text("§e/mail read §7- Nachrichten lesen"));
        player.sendMessage(Component.text("§e/mail clear §7- Postfach leeren"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("send", "read", "clear");
        if (args.length == 2 && args[0].equalsIgnoreCase("send"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
