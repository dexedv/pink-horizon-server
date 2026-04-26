package de.pinkhorizon.generators.commands;

import de.pinkhorizon.generators.GeneratorType;
import de.pinkhorizon.generators.PHGenerators;
import org.bukkit.Bukkit;
import de.pinkhorizon.generators.data.PlacedGenerator;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.*;
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
            case "blockshop", "bs" -> { plugin.getBlockShopGUI().open(player); yield true; }
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
            case "border"       -> { plugin.getBorderShopGUI().open(player); yield true; }
            case "holo"         -> { handleHolo(player, args); yield true; }
            case "pay"          -> { handlePay(player, args); yield true; }
            case "tutorial"     -> { plugin.getTutorialManager().startTutorial(player); yield true; }
            case "synergy"      -> { showSynergy(player); yield true; }
            case "list"         -> { showGeneratorList(player); yield true; }
            case "upgradeall", "ua" -> { handleUpgradeAll(player, args); yield true; }
            case "auto"         -> { toggleAutoUpgrade(player); yield true; }
            case "visit"        -> { handleVisit(player, args); yield true; }
            case "home"         -> { handleHome(player); yield true; }
            case "talents", "talent" -> { plugin.getTalentsGUI().open(player); yield true; }
            case "market"       -> { handleMarket(player, args); yield true; }
            case "milestones", "ms" -> { showMilestones(player); yield true; }
            case "tokens"       -> { showTokens(player); yield true; }
            case "event"        -> {
                if (player.hasPermission("ph.generators.admin")) handleEvent(player, args);
                else showHelp(player);
                yield true;
            }
            case "season"       -> { handleSeason(player, args); yield true; }
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
        player.sendMessage(MM.deserialize("<gray>Prestige: <light_purple>" + data.getPrestige() + "/" + plugin.getConfig().getInt("max-prestige", 1000)));
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
                    "<red>Du hast bereits das maximale Prestige (" + plugin.getConfig().getInt("max-prestige", 1000) + ") erreicht!"));
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

    private void showGeneratorList(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        if (data.getGenerators().isEmpty()) {
            player.sendMessage(MM.deserialize("<gray>Du hast keine platzierten Generatoren."));
            return;
        }

        player.sendMessage(MM.deserialize("<gold>━━ Deine Generatoren (" + data.getGenerators().size() + ") ━━"));
        for (PlacedGenerator gen : data.getGenerators()) {
            String enchantTag = gen.hasEnchant()
                    ? " <light_purple>[" + gen.getEnchant() + "]" : "";
            String maxTag = gen.getLevel() >= data.maxGeneratorLevel() ? " <yellow>[MAX]" : "";
            player.sendMessage(MM.deserialize(
                    "<dark_gray>• " + gen.getType().getDisplayName()
                    + " <gray>Lvl <white>" + gen.getLevel()
                    + " <gray>$" + String.format("%.1f", gen.incomePerSecond()) + "/s"
                    + enchantTag + maxTag
                    + " <dark_gray>(" + gen.getX() + "," + gen.getY() + "," + gen.getZ() + ")"));
        }
    }

    private void handleUpgradeAll(Player player, String[] args) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        GeneratorType filter = null;
        if (args.length >= 2) {
            try {
                filter = GeneratorType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(MM.deserialize("<red>Unbekannter Typ: " + args[1]));
                return;
            }
        }

        int count = plugin.getGeneratorManager().upgradeAll(player, filter);
        if (count == 0) {
            player.sendMessage(MM.deserialize("<yellow>Keine Generatoren upgradebar (kein Geld oder alle am Max-Level)."));
        } else {
            player.sendMessage(MM.deserialize(
                    "<green>✔ <white>" + count + " <green>Generator" + (count == 1 ? "" : "en") + " upgegraded!"));
        }
    }

    private void toggleAutoUpgrade(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        boolean newState = !data.isAutoUpgrade();
        data.setAutoUpgrade(newState);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getRepository().savePlayer(data));

        if (newState) {
            player.sendMessage(MM.deserialize(
                    "<green>✔ Auto-Upgrade <green>aktiviert! <gray>Generatoren werden automatisch geupdadet.\n"
                    + "<dark_gray>Deaktivieren: <yellow>/gen auto"));
        } else {
            player.sendMessage(MM.deserialize("<yellow>⚠ Auto-Upgrade <red>deaktiviert."));
        }
    }

    private void handleVisit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MM.deserialize("<red>Nutzung: <yellow>/gen visit <spieler>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(MM.deserialize("<red>Spieler nicht online!"));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(MM.deserialize("<red>Du kannst nicht deine eigene Insel besuchen!"));
            return;
        }

        PlayerData myData = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (myData == null) return;

        // Rückkehr-Position speichern (session-only)
        myData.setReturnLocation(player.getWorld().getName(),
                player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());

        // Zur Insel des Zielspielers teleportieren
        org.bukkit.World targetWorld = Bukkit.getWorld("island_" + target.getUniqueId());
        if (targetWorld == null) {
            player.sendMessage(MM.deserialize("<red>Die Insel von <white>" + target.getName() + " <red>ist nicht geladen."));
            return;
        }

        double sx = plugin.getConfig().getDouble("island.spawn-x", 0.5);
        double sy = plugin.getConfig().getDouble("island.spawn-y", 64.0);
        double sz = plugin.getConfig().getDouble("island.spawn-z", 0.5);
        player.teleport(new org.bukkit.Location(targetWorld, sx, sy, sz));
        player.sendMessage(MM.deserialize(
                "<green>✔ Du besuchst die Insel von <white>" + target.getName()
                + "<green>! Zurück: <yellow>/gen home"));
        target.sendMessage(MM.deserialize("<gray>✦ <white>" + player.getName() + " <gray>besucht deine Insel."));
    }

    private void handleHome(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        if (data.hasReturnLocation()) {
            // Zur gespeicherten Rückkehrposition
            org.bukkit.World returnWorld = Bukkit.getWorld(data.getReturnWorld());
            if (returnWorld != null) {
                player.teleport(new org.bukkit.Location(returnWorld,
                        data.getReturnX(), data.getReturnY(), data.getReturnZ()));
                data.clearReturnLocation();
                player.sendMessage(MM.deserialize("<green>✔ Zurückgekehrt!"));
                return;
            }
        }

        // Zur eigenen Insel
        plugin.getIslandWorldManager().loadAndTeleport(player);
        player.sendMessage(MM.deserialize("<green>✔ Zur eigenen Insel teleportiert!"));
    }

    private void handleMarket(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMarketGUI().open(player);
            return;
        }
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        switch (args[1].toLowerCase()) {
            case "buy" -> {
                if (args.length < 3) {
                    player.sendMessage(MM.deserialize("<red>Nutzung: /gen market buy <id>"));
                    return;
                }
                try {
                    int id = Integer.parseInt(args[2]);
                    MarketManager.BuyResult r = plugin.getMarketManager().buy(player, data, id);
                    switch (r) {
                        case NO_MONEY    -> player.sendMessage(MM.deserialize("<red>Nicht genug Geld!"));
                        case NOT_FOUND   -> player.sendMessage(MM.deserialize("<red>Angebot nicht gefunden!"));
                        case OWN_LISTING -> player.sendMessage(MM.deserialize("<red>Eigenes Angebot!"));
                        case SUCCESS     -> {}
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(MM.deserialize("<red>Ungültige ID!"));
                }
            }
            case "sell", "list" -> {
                if (args.length < 3) {
                    player.sendMessage(MM.deserialize("<red>Nutzung: /gen market sell <preis>"));
                    return;
                }
                try {
                    long price = Long.parseLong(args[2]);
                    MarketManager.ListResult r = plugin.getMarketManager().listToken(player, data, price);
                    if (r == MarketManager.ListResult.NOT_ENOUGH_ITEMS)
                        player.sendMessage(MM.deserialize("<red>Du hast keine Upgrade-Tokens!"));
                    else if (r == MarketManager.ListResult.INVALID_PRICE)
                        player.sendMessage(MM.deserialize("<red>Ungültiger Preis!"));
                } catch (NumberFormatException e) {
                    player.sendMessage(MM.deserialize("<red>Ungültiger Preis!"));
                }
            }
            default -> plugin.getMarketGUI().open(player);
        }
    }

    private void showMilestones(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;
        player.sendMessage(MM.deserialize(MilestoneManager.getMilestoneInfo(data)));
    }

    private void showTokens(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;
        player.sendMessage(MM.deserialize(
                "<aqua>✦ Upgrade-Tokens: <white>" + data.getUpgradeTokens() + "\n"
                + "<gray>Punkte-Token werden genutzt über:\n"
                + "<dark_gray>• /gen upgrade <gray>(im GUI als 'Token verwenden' Button)\n"
                + "<dark_gray>• /gen market sell <preis> <gray>(verkaufen)\n"
                + "<aqua>✦ Talent-Punkte: <white>" + data.getTalentPoints() + "\n"
                + "<dark_gray>• /gen talents <gray>(Talente freischalten)"));
    }

    private void handleEvent(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MM.deserialize(
                    "<yellow>Event-Befehle:\n"
                    + "<white>/gen event start <typ> <minuten>\n"
                    + "<white>/gen event stop\n"
                    + "<white>/gen event status\n"
                    + "<gray>Typen: DOUBLE_INCOME, UPGRADE_SALE, LUCKY_HOUR"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "start" -> {
                if (args.length < 4) { player.sendMessage(MM.deserialize("<red>Nutzung: /gen event start <typ> <min>")); return; }
                try {
                    EventManager.EventType type = EventManager.EventType.valueOf(args[2].toUpperCase());
                    int min = Integer.parseInt(args[3]);
                    plugin.getEventManager().startEvent(type, min);
                    player.sendMessage(MM.deserialize("<green>✔ Event gestartet!"));
                } catch (IllegalArgumentException e) {
                    player.sendMessage(MM.deserialize("<red>Ungültiger Event-Typ!"));
                }
            }
            case "stop" -> {
                plugin.getEventManager().stopEvent();
                player.sendMessage(MM.deserialize("<yellow>Event gestoppt."));
            }
            case "status" -> player.sendMessage(MM.deserialize(plugin.getEventManager().getEventStatus()));
        }
    }

    private void handleSeason(Player player, String[] args) {
        if (args.length < 2) {
            int sn = plugin.getSeasonalLeaderboardManager().getCurrentSeasonNo();
            player.sendMessage(MM.deserialize(
                plugin.getSeasonalLeaderboardManager().getSeasonLeaderboardText(sn)));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "snapshot" -> {
                if (!player.hasPermission("ph.generators.admin")) {
                    player.sendMessage(MM.deserialize("<red>Keine Berechtigung!")); return;
                }
                plugin.getSeasonalLeaderboardManager().takeSnapshot(player.getName());
            }
            case "top" -> {
                int sn = args.length >= 3 ? parseInt(args[2], 0) : 0;
                player.sendMessage(MM.deserialize(
                    plugin.getSeasonalLeaderboardManager().getSeasonLeaderboardText(sn)));
            }
            default -> player.sendMessage(MM.deserialize(
                "<yellow>Befehle: /gen season top [nr] | /gen season snapshot (Admin)"));
        }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
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

    private void handlePay(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MM.deserialize(
                    "<red>Nutzung: <yellow>/gen pay <spieler> <betrag>"));
            return;
        }

        PlayerData senderData = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (senderData == null) return;

        // Ziel-Spieler suchen
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(MM.deserialize(
                    "<red>Spieler <white>" + args[1] + " <red>ist nicht online!"));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(MM.deserialize("<red>Du kannst kein Geld an dich selbst senden!"));
            return;
        }

        PlayerData targetData = plugin.getPlayerDataMap().get(target.getUniqueId());
        if (targetData == null) {
            player.sendMessage(MM.deserialize("<red>Spielerdaten von " + target.getName() + " nicht geladen!"));
            return;
        }

        // Betrag parsen
        long amount;
        try {
            amount = Long.parseLong(args[2].replace("_", "").replace(".", ""));
        } catch (NumberFormatException e) {
            player.sendMessage(MM.deserialize("<red>Ungültiger Betrag: <white>" + args[2]));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(MM.deserialize("<red>Der Betrag muss größer als 0 sein!"));
            return;
        }
        if (!senderData.takeMoney(amount)) {
            player.sendMessage(MM.deserialize(
                    "<red>Nicht genug Geld! Du hast <yellow>$"
                    + MoneyManager.formatMoney(senderData.getMoney())
                    + "<red>, benötigt: <yellow>$" + MoneyManager.formatMoney(amount)));
            return;
        }

        targetData.addMoney(amount);

        // Beide asynchron speichern
        final PlayerData sd = senderData;
        final PlayerData td = targetData;
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getRepository().savePlayer(sd);
            plugin.getRepository().savePlayer(td);
        });

        player.sendMessage(MM.deserialize(
                "<green>✔ <yellow>$" + MoneyManager.formatMoney(amount)
                + "<green> an <white>" + target.getName()
                + "<green> gesendet. Dein Guthaben: <yellow>$"
                + MoneyManager.formatMoney(senderData.getMoney())));

        target.sendMessage(MM.deserialize(
                "<green>✦ <white>" + player.getName()
                + "<green> hat dir <yellow>$" + MoneyManager.formatMoney(amount)
                + "<green> gesendet! Dein Guthaben: <yellow>$"
                + MoneyManager.formatMoney(targetData.getMoney())));
    }

    private void handleHolo(Player player, String[] args) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) { player.sendMessage(MM.deserialize("<red>Daten nicht geladen!")); return; }

        String sub = args.length >= 2 ? args[1].toLowerCase() : "set";

        if (sub.equals("remove")) {
            if (!data.hasStatsHolo()) {
                player.sendMessage(MM.deserialize("<red>Du hast kein Stats-Hologramm gesetzt."));
                return;
            }
            plugin.getHologramManager().removeStatsHolo(player.getUniqueId());
            data.clearHoloLocation();
            plugin.getRepository().clearHoloLocation(player.getUniqueId());
            player.sendMessage(MM.deserialize("<yellow>⚙ Stats-Hologramm entfernt."));
            return;
        }

        if (sub.equals("lb")) {
            org.bukkit.Location loc = player.getLocation().add(0, 1.5, 0);
            loc.setX(loc.getBlockX() + 0.5);
            loc.setZ(loc.getBlockZ() + 0.5);
            if (data.hasLbHolo()) {
                plugin.getHologramManager().removeLbHolo(player.getUniqueId());
            }
            plugin.getHologramManager().setLbHolo(player.getUniqueId(), loc);
            data.setLbHoloLocation(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    plugin.getRepository().saveLbHoloLocation(player.getUniqueId(),
                            loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()));
            player.sendMessage(MM.deserialize("<green>✔ Ranglisten-Hologramm platziert! <gray>Entfernen: <yellow>/gen holo lbremove"));
            return;
        }

        if (sub.equals("lbremove")) {
            if (!data.hasLbHolo()) {
                player.sendMessage(MM.deserialize("<red>Du hast kein Ranglisten-Hologramm gesetzt."));
                return;
            }
            plugin.getHologramManager().removeLbHolo(player.getUniqueId());
            data.clearLbHoloLocation();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    plugin.getRepository().clearLbHoloLocation(player.getUniqueId()));
            player.sendMessage(MM.deserialize("<yellow>⚙ Ranglisten-Hologramm entfernt."));
            return;
        }

        // "set" – Stats-Hologramm an aktueller Position platzieren
        org.bukkit.Location loc = player.getLocation().add(0, 1.5, 0);
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);

        if (data.hasStatsHolo()) {
            plugin.getHologramManager().removeStatsHolo(player.getUniqueId());
        }

        plugin.getHologramManager().setStatsHolo(player.getUniqueId(), loc);
        data.setHoloLocation(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        plugin.getRepository().saveHoloLocation(player.getUniqueId(),
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());

        player.sendMessage(MM.deserialize("<green>✔ Stats-Hologramm platziert! <gray>Entfernen: <yellow>/gen holo remove"));
    }

    private void showHelp(Player player) {
        player.sendMessage(MM.deserialize("""
            <gold>━━ IdleForge Befehle ━━
            <yellow>/gen shop <gray>- Generator-Shop
            <yellow>/gen upgrade <gray>- Generatoren upgraden
            <yellow>/gen upgradeall [typ] <gray>- Alle upgraden
            <yellow>/gen auto <gray>- Auto-Upgrade umschalten
            <yellow>/gen list <gray>- Generatoren-Liste
            <yellow>/gen talents <gray>- Talent-Baum öffnen
            <yellow>/gen market <gray>- Marktplatz (Tokens)
            <yellow>/gen tokens <gray>- Tokens & Talentpunkte
            <yellow>/gen milestones <gray>- Meilensteine
            <yellow>/gen visit <spieler> <gray>- Insel besuchen
            <yellow>/gen home <gray>- Zur eigenen Insel
            <yellow>/gen stats <gray>- Statistiken
            <yellow>/gen pay <spieler> <betrag> <gray>- Geld senden
            <yellow>/gen balance <gray>- Kontostand
            <yellow>/gen top <gray>- Leaderboard
            <yellow>/gen season [top|snapshot] <gray>- Saison-LB
            <yellow>/gen prestige <gray>- Prestige machen
            <yellow>/gen fuse <gray>- Fusionieren
            <yellow>/gen quests <gray>- Quests
            <yellow>/gen achievements <gray>- Achievements
            <yellow>/gen border <gray>- Insel-Grenze
            <yellow>/gen synergy <gray>- Synergien
            <yellow>/gen guild <create|join|leave|info> <gray>- Gilden
            <yellow>/gen holo set/remove/lb/lbremove <gray>- Hologramme
            <yellow>/booster status <gray>- Booster-Status"""));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("shop", "upgrade", "upgradeall", "auto", "list", "talents",
                    "market", "tokens", "milestones", "visit", "home",
                    "balance", "stats", "top", "season", "prestige",
                    "fuse", "quests", "achievements", "guild", "guildtop", "synergy", "border",
                    "holo", "pay", "tutorial", "help", "event");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("guild")) {
            return Arrays.asList("create", "join", "leave", "info");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("holo")) {
            return Arrays.asList("set", "remove", "lb", "lbremove");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("market")) {
            return Arrays.asList("buy", "sell");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("season")) {
            return Arrays.asList("top", "snapshot");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("event")) {
            return Arrays.asList("start", "stop", "status");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("start")) {
            return Arrays.asList("DOUBLE_INCOME", "UPGRADE_SALE", "LUCKY_HOUR");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("visit")) {
            String prefix = args[1].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && p.getName().toLowerCase().startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pay")) {
            String prefix = args[1].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && p.getName().toLowerCase().startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
