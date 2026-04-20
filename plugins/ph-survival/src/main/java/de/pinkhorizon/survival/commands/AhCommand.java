package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.gui.AhGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AhCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;
    private final AhGui      gui;

    public AhCommand(PHSurvival plugin, AhGui gui) {
        this.plugin = plugin;
        this.gui    = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur für Spieler.");
            return true;
        }

        if (args.length == 0) {
            gui.openBrowse(player, 0);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length < 2) {
                player.sendMessage("§cVerwendung: /ah sell <Preis>");
                return true;
            }
            long price;
            try {
                price = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cUngültiger Preis.");
                return true;
            }
            if (price < 1) {
                player.sendMessage("§cDer Preis muss mindestens 1 Coin betragen.");
                return true;
            }
            if (price > 10_000_000L) {
                player.sendMessage("§cMaximaler Preis: 10.000.000 Coins.");
                return true;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                player.sendMessage("§cHalte ein Item in der Hand.");
                return true;
            }
            String err = plugin.getAuctionManager().addListing(
                    player.getUniqueId(), player.getName(), held.clone(), price);
            if (err != null) {
                player.sendMessage("§c" + err);
                return true;
            }
            held.setAmount(0); // aus Inventar entfernen
            player.sendMessage("§aItem für §6" + price + " Coins §aeingestellt!");
            return true;
        }

        player.sendMessage("§cVerwendung: /ah [sell <Preis>]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("sell");
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) return List.of("<Preis>");
        return List.of();
    }
}
