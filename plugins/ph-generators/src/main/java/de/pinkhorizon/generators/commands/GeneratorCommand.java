package de.pinkhorizon.generators.commands;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.GeneratorManager;
import de.pinkhorizon.generators.managers.GuildManager;
import de.pinkhorizon.generators.managers.MoneyManager;
import de.pinkhorizon.generators.managers.PrestigeManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Haupt-Command /gen mit Sub-Commands.
 */
public class GeneratorCommand implements CommandExecutor, TabCompleter {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public GeneratorCommand(PHGenerators plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen!");
            return true;
        }

        // /booster
        if (label.equalsIgnoreCase("booster")) {
            return handleBooster(player, args);
        }

        // /gen
        if (args.length == 0) {
            plugin.getShopGUI().open(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "shop"         -> { plugin.getShopGUI().open(player); yield true; }
            case "upgrade", "up"-> { plugin.getUpgradeGUI().open(player); yield true; }
            case "balance", "bal"-> { showBalance(player); yield true; }
            case "stats"        -> { showStats(player); yield true; }
            case "top"          -> { showTop(player); yield true; }
            case "prestige"     -> { handlePrestige(player); yield true; }
            case "fuse"         -> { handleFuse(player, args); yield true; }
            case "quests", "q"  -> { showQuests(player); yield true; }
            case "achievements", "ach" -> { showAchievements(player); yield true; }
            case "guild"        -> { handleGuild(player, args); yield true; }
            case "guildtop"     -> { showGuildTop(player); yield true; }
            case "synergy"      -> { showSynergy(player); yield true; }
            case "help"         -> { showHelp(player); yield true; }
            case "give"         -> {
                if (player.hasPermission("ph.generators.admin")) handleAdminGive(player, args);
                else showHelp(player);
                yield true;
            }
            case "setmoney"     -> {
                if (player.hasPermission("ph.generators.admin")) handleSetMoney(player, args);
                else showHelp(player);
                yield true;
            }
            default -> { showHelp(player); yield true; }
        };
    }

    // ── Sub-Commands ─────────────────────────────────────────────────────────

    private void showBalance(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;
        player.sendMessage(MM.deserialize(
                "<gold>Dein Guthaben: <green>$" + MoneyManager.formatMoney(data.getMoney())
                        + " <gray>| Prestige: <light_purple>" + data.getPrestige()
                        + " <gray>| Max-Level: <aqua>" + data.maxGeneratorLevel()));
    }

    private void showStats(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        double totalIncome = data.getGenerators().stream()
                .mapToDouble(PlacedGenerator::incomePerSecond).sum();
        double totalMult = data.prestigeMultiplier()
                * data.effectiveBoosterMultiplier()
                * plugin.getMoneyManager().getServerBoosterMultiplier()
                * plugin.getSynergyManager().getTotalSynergyMultiplier(data);

        player.sendMessage(MM.deserialize("<gold>━━ Deine Generator-Stats ━━"));
        player.sendMessage(MM.deserialize("<gray>Geld: <green>$" + MoneyManager.formatMoney(data.getMoney())));
        player.sendMessage(MM.deserialize("<gray>Prestige: <light_purple>" + data.getPrestige() + "/" + plugin.getConfig().getInt("max-prestige", 50)));
        player.sendMessage(MM.deserialize("<gray>Nächstes Prestige kostet: <yellow>$" + MoneyManager.formatMoney(data.nextPrestigeCost())));
        player.sendMessage(MM.deserialize("<gray>Max. Generator-Level: <aqua>" + data.maxGeneratorLevel()));
        player.sendMessage(MM.deserialize("<gray>Generatoren: <white>" + data.getGenerators().size()));
        player.sendMessage(MM.deserialize("<gray>Einkommen/s (brutto): <green>$" + String.format("%.2f", totalIncome)));
        player.sendMessage(MM.deserialize("<gray>Gesamt-Multiplikator: <gold>x" + String.format("%.2f", totalMult)));
        player.sendMessage(MM.deserialize("<gray>Einkommen/s (netto): <green>$" + String.format("%.2f", totalIncome * totalMult)));
        player.sendMessage(MM.deserialize("<gray>Upgrades gesamt: <white>" + data.getTotalUpgrades()));

        // Booster-Status
        player.sendMessage(MM.deserialize(plugin.getBoosterManager().getBoosterStatus(data)));
        if (plugin.getMoneyManager().isServerBoosterActive()) {
            long rem = plugin.getMoneyManager().getServerBoosterExpiry() - System.currentTimeMillis() / 1000;
            player.sendMessage(MM.deserialize("<gold>Server-Booster: <yellow>x"
                    + plugin.getMoneyManager().getServerBoosterMultiplier()
                    + " <gray>– noch " + rem / 60 + "m " + rem % 60 + "s"));
        }
    }

    private void showTop(Player player) {
        player.sendMessage(MM.deserialize(plugin.getLeaderboardManager().getLeaderboardText()));
    }

    private void handlePrestige(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        PrestigeManager.PrestigeResult result = plugin.getPrestigeManager().tryPrestige(player);
        switch (result) {
            case NO_MONEY -> player.sendMessage(MM.deserialize(
                    "<red>Nicht genug Geld! Benötigt: $"
                            + MoneyManager.formatMoney(data.nextPrestigeCost())
                            + " | Du hast: $" + MoneyManager.formatMoney(data.getMoney())));
            case MAX_REACHED -> player.sendMessage(MM.deserialize(
                    "<red>Du hast bereits das maximale Prestige (" + plugin.getConfig().getInt("max-prestige", 50) + ") erreicht!"));
            case SUCCESS -> {} // Nachricht wird im Manager gesendet
            default -> {}
        }
    }

    private void handleFuse(Player player, String[] args) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        // Syntax: /gen fuse <x1,y1,z1> <x2,y2,z2> <x3,y3,z3>
        if (args.length < 4) {
            player.sendMessage(MM.deserialize(
                    "<yellow>Nutzung: <white>/gen fuse <x,y,z> <x,y,z> <x,y,z>\n"
                            + "<gray>Wähle 3 gleiche Max-Level-Generatoren zum Fusionieren.\n"
                            + "<gray>Beispiel: /gen fuse 100,64,200 101,64,200 102,64,200"));
            return;
        }

        List<PlacedGenerator> targets = new ArrayList<>();
        String world = player.getWorld().getName();

        for (int i = 1; i <= 3; i++) {
            String[] coords = args[i].split(",");
            if (coords.length != 3) {
                player.sendMessage(MM.deserialize("<red>Ungültiges Format! Nutze x,y,z (z.B. 100,64,200)"));
                return;
            }
            try {
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);
                org.bukkit.Location loc = new org.bukkit.Location(player.getWorld(), x, y, z);
                PlacedGenerator gen = plugin.getGeneratorManager().getAt(loc);
                if (gen == null) {
                    player.sendMessage(MM.deserialize("<red>Kein Generator an Position " + args[i] + "!"));
                    return;
                }
                targets.add(gen);
            } catch (NumberFormatException e) {
                player.sendMessage(MM.deserialize("<red>Ungültige Koordinaten: " + args[i]));
                return;
            }
        }

        GeneratorManager.FuseResult result = plugin.getGeneratorManager().fuse(player, targets);
        switch (result) {
            case SUCCESS -> player.sendMessage(MM.deserialize(
                    "<gold>✦ Fusion erfolgreich! <yellow>Mega-Generator erstellt!"));
            case DIFFERENT_TYPE -> player.sendMessage(MM.deserialize(
                    "<red>Alle 3 Generatoren müssen denselben Typ haben!"));
            case NOT_MAX_LEVEL -> player.sendMessage(MM.deserialize(
                    "<red>Alle 3 Generatoren müssen das maximale Level haben!"));
            case ALREADY_MEGA -> player.sendMessage(MM.deserialize(
                    "<red>Mega-Generatoren können nicht weiter fusioniert werden!"));
            case NOT_OWNER -> player.sendMessage(MM.deserialize(
                    "<red>Du bist nicht der Besitzer aller Generatoren!"));
            default -> player.sendMessage(MM.deserialize("<red>Fusion fehlgeschlagen."));
        }
    }

    private void showQuests(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;
        player.sendMessage(MM.deserialize(plugin.getQuestManager().getQuestOverview(data)));
    }

    private void showAchievements(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;
        player.sendMessage(MM.deserialize(plugin.getAchievementManager().getAchievementList(data)));
    }

    private void handleGuild(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MM.deserialize(plugin.getGuildManager().getGuildInfo(player.getUniqueId())));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) { player.sendMessage(MM.deserialize("<red>Nutzung: /gen guild create <name>")); return; }
                var result = plugin.getGuildManager().create(player, args[2]);
                switch (result) {
                    case ALREADY_IN_GUILD -> player.sendMessage(MM.deserialize("<red>Du bist bereits in einer Gilde!"));
                    case INVALID_NAME -> player.sendMessage(MM.deserialize("<red>Name muss 3-16 Zeichen lang sein!"));
                    case NAME_TAKEN -> player.sendMessage(MM.deserialize("<red>Dieser Gildenname ist bereits vergeben!"));
                    case DB_ERROR -> player.sendMessage(MM.deserialize("<red>Datenbankfehler. Bitte Admin kontaktieren."));
                    default -> {}
                }
            }
            case "join" -> {
                if (args.length < 3) { player.sendMessage(MM.deserialize("<red>Nutzung: /gen guild join <name>")); return; }
                var result = plugin.getGuildManager().join(player, args[2]);
                switch (result) {
                    case ALREADY_IN_GUILD -> player.sendMessage(MM.deserialize("<red>Du bist bereits in einer Gilde!"));
                    case NOT_FOUND -> player.sendMessage(MM.deserialize("<red>Gilde nicht gefunden!"));
                    case FULL -> player.sendMessage(MM.deserialize("<red>Die Gilde ist voll (max. 5 Mitglieder)!"));
                    case SUCCESS -> player.sendMessage(MM.deserialize("<green>✔ Gilde beigetreten!"));
                }
            }
            case "leave" -> {
                var result = plugin.getGuildManager().leave(player);
                if (result == GuildManager.LeaveResult.NOT_IN_GUILD)
                    player.sendMessage(MM.deserialize("<red>Du bist in keiner Gilde!"));
            }
            case "info" -> player.sendMessage(MM.deserialize(plugin.getGuildManager().getGuildInfo(player.getUniqueId())));
            default -> player.sendMessage(MM.deserialize(
                    "<yellow>Guild-Befehle: <white>create, join, leave, info"));
        }
    }

    private void showGuildTop(Player player) {
        player.sendMessage(MM.deserialize(plugin.getGuildManager().getGuildTop()));
    }

    private void showSynergy(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;
        player.sendMessage(MM.deserialize(plugin.getSynergyManager().getSynergyInfo(data)));
    }

    private boolean handleBooster(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(MM.deserialize("<yellow>Befehle: /booster activate <mult> <min> (Admin) | /booster status"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "activate" -> {
                if (!player.hasPermission("ph.generators.booster.admin")) {
                    player.sendMessage(MM.deserialize("<red>Keine Berechtigung!")); return true;
                }
                if (args.length < 3) {
                    player.sendMessage(MM.deserialize("<red>Nutzung: /booster activate <multiplikator> <minuten>")); return true;
                }
                double mult = Double.parseDouble(args[1]);
                int min = Integer.parseInt(args[2]);
                plugin.getBoosterManager().activateServerBooster(mult, min);
            }
            case "status" -> {
                PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
                if (data == null) return true;
                player.sendMessage(MM.deserialize(plugin.getBoosterManager().getBoosterStatus(data)));
                if (plugin.getMoneyManager().isServerBoosterActive()) {
                    long rem = plugin.getMoneyManager().getServerBoosterExpiry() - System.currentTimeMillis() / 1000;
                    player.sendMessage(MM.deserialize("<gold>Server-Booster aktiv: x"
                            + plugin.getMoneyManager().getServerBoosterMultiplier()
                            + " <gray>| noch " + rem / 60 + "m " + rem % 60 + "s"));
                } else {
                    player.sendMessage(MM.deserialize("<gray>Kein Server-Booster aktiv."));
                }
            }
        }
        return true;
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    private void handleAdminGive(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MM.deserialize("<red>Nutzung: /gen give <spieler> <generatorTyp>")); return;
        }
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (target == null) { player.sendMessage(MM.deserialize("<red>Spieler nicht gefunden!")); return; }
        try {
            var type = de.pinkhorizon.generators.GeneratorType.valueOf(args[2].toUpperCase());
            target.getInventory().addItem(plugin.getGeneratorManager().createGeneratorItem(type, 1));
            player.sendMessage(MM.deserialize("<green>Gegeben: " + type.getDisplayName() + " an " + target.getName()));
        } catch (IllegalArgumentException e) {
            player.sendMessage(MM.deserialize("<red>Unbekannter Generator-Typ: " + args[2]));
        }
    }

    private void handleSetMoney(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MM.deserialize("<red>Nutzung: /gen setmoney <spieler> <betrag>")); return;
        }
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (target == null) { player.sendMessage(MM.deserialize("<red>Spieler nicht online!")); return; }
        PlayerData data = plugin.getPlayerDataMap().get(target.getUniqueId());
        if (data == null) return;
        try {
            long amount = Long.parseLong(args[2]);
            data.setMoney(amount);
            player.sendMessage(MM.deserialize("<green>Geld von " + target.getName() + " auf $" + amount + " gesetzt."));
        } catch (NumberFormatException e) {
            player.sendMessage(MM.deserialize("<red>Ungültiger Betrag!"));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(MM.deserialize("""
            <gold>━━ IdleForge Befehle ━━
            <yellow>/gen shop <gray>- Generator-Shop öffnen
            <yellow>/gen upgrade <gray>- Generatoren upgraden
            <yellow>/gen stats <gray>- Deine Statistiken
            <yellow>/gen balance <gray>- Kontostand
            <yellow>/gen top <gray>- Leaderboard
            <yellow>/gen prestige <gray>- Prestige machen
            <yellow>/gen fuse <gray>- Generatoren fusionieren
            <yellow>/gen quests <gray>- Tages-/Wochen-Quests
            <yellow>/gen achievements <gray>- Achievements
            <yellow>/gen synergy <gray>- Synergie-Boni anzeigen
            <yellow>/gen guild <create|join|leave|info> <gray>- Gilden
            <yellow>/gen guildtop <gray>- Gilden-Leaderboard
            <yellow>/booster status <gray>- Booster-Status"""));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("shop", "upgrade", "balance", "stats", "top", "prestige",
                    "fuse", "quests", "achievements", "guild", "guildtop", "synergy", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("guild")) {
            return Arrays.asList("create", "join", "leave", "info");
        }
        return List.of();
    }
}
