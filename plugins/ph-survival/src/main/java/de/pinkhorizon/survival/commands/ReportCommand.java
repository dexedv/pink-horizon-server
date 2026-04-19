package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;
    private final File reportFile;
    private final YamlConfiguration reports;
    private int reportCounter;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public ReportCommand(PHSurvival plugin) {
        this.plugin = plugin;
        reportFile = new File(plugin.getDataFolder(), "reports.yml");
        reports = YamlConfiguration.loadConfiguration(reportFile);
        reportCounter = reports.getKeys(false).size();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player reporter)) {
            sender.sendMessage("§cNur für Spieler!");
            return true;
        }

        if (args.length < 2) {
            reporter.sendMessage("§cVerwendung: /report <Spieler> <Grund>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            reporter.sendMessage("§cSpieler §f" + args[0] + " §cist nicht online!");
            return true;
        }

        if (target.equals(reporter)) {
            reporter.sendMessage("§cDu kannst dich nicht selbst melden!");
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String id = "report_" + (++reportCounter);

        reports.set(id + ".reporter", reporter.getName());
        reports.set(id + ".target", target.getName());
        reports.set(id + ".reason", reason);
        reports.set(id + ".time", timestamp);
        try { reports.save(reportFile); } catch (IOException ignored) {}

        reporter.sendMessage("§aDu hast §f" + target.getName() + " §aerfolgreich gemeldet.");

        // Alle OPs benachrichtigen
        String alert = "§c[Report] §f" + reporter.getName() + " §7hat §f" + target.getName()
                + " §7gemeldet: §c" + reason;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp()) {
                online.sendMessage(alert);
                online.playSound(online.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        }
        plugin.getLogger().info("[Report] " + reporter.getName() + " -> " + target.getName() + ": " + reason);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (!p.equals(sender)) names.add(p.getName());
            });
            return names.stream().filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
