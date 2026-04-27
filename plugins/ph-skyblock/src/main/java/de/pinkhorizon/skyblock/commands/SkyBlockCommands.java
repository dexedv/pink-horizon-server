package de.pinkhorizon.skyblock.commands;

import de.pinkhorizon.skyblock.PHSkyBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handler für alle neuen SkyBlock-Befehle:
 * /skydna, /skyrituals, /skyweather, /skychronicle, /skycontract
 */
public class SkyBlockCommands implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;

    public SkyBlockCommands(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur für Spieler.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "skydna" -> plugin.getIslandDnaManager().showDna(player);
            case "skyrituals" -> plugin.getRitualManager().showRituals(player);
            case "skyweather" -> {
                var w = plugin.getWeatherManager();
                player.sendMessage(MM.deserialize(
                    "<dark_gray>[Wetter] <white>Aktuell: " + w.getCurrent().displayName));
                player.sendMessage(MM.deserialize(
                    "<dark_gray>[Wetter] <white>Als nächstes: " + w.getNext().displayName));
                if (!w.getCurrent().effect.isEmpty()) {
                    player.sendMessage(MM.deserialize(
                        "<gray>Effekt: " + w.getCurrent().effect));
                }
            }
            case "skychronicle" -> plugin.getChronicleManager().giveChronicle(player);
            case "skycontract" -> {
                if (args.length == 0) {
                    plugin.getContractManager().showBoard(player);
                } else {
                    switch (args[0].toLowerCase()) {
                        case "list" -> plugin.getContractManager().showBoard(player);
                        default -> player.sendMessage(MM.deserialize(
                            "<gray>Nutze: <yellow>/skycontract list"));
                    }
                }
            }
            case "skystory" -> plugin.getStoryManager().showStoryStatus(player);
            case "blueprint" -> {
                if (args.length == 0) {
                    plugin.getBlueprintManager().listBlueprints(player);
                } else {
                    switch (args[0].toLowerCase()) {
                        case "save"  -> {
                            if (args.length < 2) { player.sendMessage(MM.deserialize("<red>Usage: /blueprint save <name>")); break; }
                            plugin.getBlueprintManager().saveBlueprint(player, args[1]);
                        }
                        case "load"  -> {
                            if (args.length < 2) { player.sendMessage(MM.deserialize("<red>Usage: /blueprint load <name>")); break; }
                            plugin.getBlueprintManager().loadBlueprint(player, args[1]);
                        }
                        case "share" -> {
                            if (args.length < 2) { player.sendMessage(MM.deserialize("<red>Usage: /blueprint share <name>")); break; }
                            plugin.getBlueprintManager().shareBlueprint(player, args[1]);
                        }
                        case "list"  -> plugin.getBlueprintManager().listBlueprints(player);
                        default -> player.sendMessage(MM.deserialize(
                            "<gray>Nutze: <yellow>/blueprint [save|load|share|list]"));
                    }
                }
            }
        }
        return true;
    }
}
