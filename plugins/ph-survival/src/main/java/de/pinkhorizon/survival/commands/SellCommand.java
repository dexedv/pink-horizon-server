package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SellCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public SellCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cNur Spieler!");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage("§cDieser Befehl ist nicht verfügbar!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("\u00a7cVerwendung: /sell <hand|all>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "hand" -> sellHand(player);
            case "all" -> sellAll(player);
            default -> player.sendMessage("\u00a7cVerwendung: /sell <hand|all>");
        }
        return true;
    }

    private void sellHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || item.getAmount() == 0) {
            player.sendMessage("\u00a7cDu h\u00e4ltst nichts in der Hand!");
            return;
        }
        int price = getSellPrice(item.getType());
        if (price <= 0) {
            player.sendMessage("\u00a7cDieses Item kann nicht verkauft werden.");
            return;
        }
        long total = (long) price * item.getAmount();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        plugin.getEconomyManager().deposit(player.getUniqueId(), total);
        player.sendMessage("\u00a7aVerkauft: \u00a7f" + item.getAmount() + "x " + item.getType().name()
                + "\u00a7a f\u00fcr \u00a7f" + total + "\u00a7a Coins!");
    }

    private void sellAll(Player player) {
        long totalEarned = 0;
        int itemsSold = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            int price = getSellPrice(item.getType());
            if (price <= 0) continue;
            totalEarned += (long) price * item.getAmount();
            itemsSold += item.getAmount();
            contents[i] = null;
        }
        if (itemsSold == 0) {
            player.sendMessage("\u00a7cKeine verkäuflichen Items im Inventar!");
            return;
        }
        player.getInventory().setContents(contents);
        plugin.getEconomyManager().deposit(player.getUniqueId(), totalEarned);
        player.sendMessage("\u00a7aAlles verkauft! \u00a7f" + itemsSold + "\u00a7a Items f\u00fcr \u00a7f"
                + totalEarned + "\u00a7a Coins!");
    }

    private int getSellPrice(Material material) {
        return plugin.getConfig().getInt("shop.sell-prices." + material.name(), 0);
    }
}
