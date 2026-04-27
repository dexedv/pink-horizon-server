package de.pinkhorizon.skyblock.commands;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Generator;
import de.pinkhorizon.skyblock.gui.AchievementGui;
import de.pinkhorizon.skyblock.gui.MainMenuGui;
import de.pinkhorizon.skyblock.gui.QuestGui;
import de.pinkhorizon.skyblock.gui.ShopGui;
import de.pinkhorizon.skyblock.gui.TitleGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /gen  – Generator & Coins Befehle
 *
 * Unterkommandos:
 *   /gen            → Hauptmenü
 *   /gen bal        → Kontostand
 *   /gen quests     → Quest-GUI
 *   /gen achievements → Achievement-GUI
 *   /gen titles     → Titel-GUI
 *   /gen top        → Top-10 nach Coins (Chat)
 *   /gen give       → [admin] Generator-Item geben
 *   /gen reload     → [admin] NPCs neu laden
 */
public class GeneratorCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX =
        "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] <white>";

    private final PHSkyBlock plugin;

    public GeneratorCommand(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur für Spieler.");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "";

        switch (sub) {
            case "bal", "balance", "coins" -> {
                plugin.getCoinManager().sendBalance(player);
            }
            case "quests", "quest", "q" -> {
                new QuestGui(plugin, player).open(player);
            }
            case "achievements", "achievement", "ach", "a" -> {
                new AchievementGui(plugin, player).open(player);
            }
            case "titles", "title", "titel", "t" -> {
                new TitleGui(plugin, player).open(player);
            }
            case "top", "top coins" -> plugin.getLeaderboardManager().showTopCoins(player);
            case "top quests"       -> plugin.getLeaderboardManager().showTopQuests(player);
            case "top mined"        -> plugin.getLeaderboardManager().showTopMined(player);
            case "shop"             -> new ShopGui(plugin, player).open(player);
            case "give" -> {
                if (!player.hasPermission("skyblock.admin")) {
                    player.sendMessage(MM.deserialize(PREFIX + "<red>Keine Berechtigung."));
                    return true;
                }
                var item = plugin.getGeneratorManager().createGeneratorItem();
                player.getInventory().addItem(item);
                player.sendMessage(MM.deserialize(PREFIX + "<green>Generator erhalten."));
            }
            case "reload" -> {
                if (!player.hasPermission("skyblock.admin")) {
                    player.sendMessage(MM.deserialize(PREFIX + "<red>Keine Berechtigung."));
                    return true;
                }
                plugin.getNpcManager().reloadNpcs();
                player.sendMessage(MM.deserialize(PREFIX + "<green>NPCs neu geladen."));
            }
            case "help", "" -> {
                new MainMenuGui(plugin, player).open(player);
            }
            default -> {
                player.sendMessage(MM.deserialize(
                    PREFIX + "<red>Unbekannter Befehl. Nutze <yellow>/phsk help</yellow>."));
            }
        }
        return true;
    }

    private void showTop(Player player) {
        player.sendMessage(MM.deserialize(
            "<light_purple>━━━ <bold>Top 10 – Meiste Coins</bold> ━━━"));

        List<Generator> sorted = plugin.getGeneratorManager().getGeneratorsOf(player.getUniqueId());
        // Da wir keinen server-weiten Top-Query haben, zeigen wir die eigenen Generatoren
        // In einer späteren Version würde hier ein async DB-Query stehen
        if (sorted.isEmpty()) {
            player.sendMessage(MM.deserialize("<gray>Keine Generatoren gefunden."));
        } else {
            long coins = plugin.getCoinManager().getCoins(player.getUniqueId());
            int gens   = sorted.size();
            int maxLvl = sorted.stream().mapToInt(Generator::getLevel).max().orElse(0);
            long produced = sorted.stream().mapToLong(Generator::getTotalProduced).sum();

            player.sendMessage(MM.deserialize(
                "<gray>Deine Generatoren: <yellow>" + gens));
            player.sendMessage(MM.deserialize(
                "<gray>Höchstes Level: <gold>" + maxLvl));
            player.sendMessage(MM.deserialize(
                "<gray>Total produziert: <white>" + String.format("%,d", produced)));
            player.sendMessage(MM.deserialize(
                "<gray>Aktuelle Coins: <gold>" + String.format("%,d", coins)));
        }

        player.sendMessage(MM.deserialize("<light_purple>━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return List.of("bal", "quests", "achievements", "titles", "top", "shop", "give", "reload", "help")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            return List.of("coins", "quests", "mined")
                .stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
