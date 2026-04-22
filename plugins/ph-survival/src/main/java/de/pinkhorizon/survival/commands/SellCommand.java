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
            sender.sendMessage("§cNur Spieler!");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§cVerwendung: /sell <hand|all>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "hand" -> sellHand(player);
            case "all"  -> sellAll(player);
            default     -> player.sendMessage("§cVerwendung: /sell <hand|all>");
        }
        return true;
    }

    // ── Hand ──────────────────────────────────────────────────────────────────

    private void sellHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || item.getAmount() == 0) {
            player.sendMessage("§cDu hältst nichts in der Hand!");
            return;
        }

        int price = getSellPrice(item.getType());
        if (price > 0) {
            long total = (long) price * item.getAmount();
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            plugin.getEconomyManager().deposit(player.getUniqueId(), total);
            player.sendMessage("§aVerkauft: §f" + item.getAmount() + "x " + fmtName(item.getType())
                    + " §afür §f" + total + " §aCoins!");
            return;
        }

        int batch = getBatchSize(item.getType());
        if (batch > 0) {
            if (item.getAmount() < batch) {
                player.sendMessage("§cDu brauchst mindestens §f" + batch + "x §c"
                        + fmtName(item.getType()) + " §cfür 1 Coin.");
                return;
            }
            int earned = item.getAmount() / batch;
            int used   = earned * batch;
            int left   = item.getAmount() - used;
            player.getInventory().setItemInMainHand(left > 0 ? new ItemStack(item.getType(), left) : new ItemStack(Material.AIR));
            plugin.getEconomyManager().deposit(player.getUniqueId(), earned);
            player.sendMessage("§aVerkauft: §f" + used + "x " + fmtName(item.getType())
                    + " §afür §f" + earned + " §aCoins! §8(§7" + batch + " Stück = 1 Coin§8)");
            return;
        }

        player.sendMessage("§cDieses Item kann nicht verkauft werden.");
    }

    // ── All ───────────────────────────────────────────────────────────────────

    private void sellAll(Player player) {
        long totalEarned = 0;
        int  itemsSold   = 0;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;

            int price = getSellPrice(item.getType());
            if (price > 0) {
                totalEarned += (long) price * item.getAmount();
                itemsSold   += item.getAmount();
                contents[i]  = null;
                continue;
            }

            int batch = getBatchSize(item.getType());
            if (batch > 0 && item.getAmount() >= batch) {
                int earned = item.getAmount() / batch;
                int used   = earned * batch;
                int left   = item.getAmount() - used;
                totalEarned += earned;
                itemsSold   += used;
                contents[i]  = left > 0 ? new ItemStack(item.getType(), left) : null;
            }
        }

        if (itemsSold == 0) {
            player.sendMessage("§cKeine verkäuflichen Items im Inventar!");
            return;
        }
        player.getInventory().setContents(contents);
        plugin.getEconomyManager().deposit(player.getUniqueId(), totalEarned);
        player.sendMessage("§aAlles verkauft! §f" + itemsSold + "§a Items für §f" + totalEarned + "§a Coins!");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getSellPrice(Material m) {
        return plugin.getConfig().getInt("shop.sell-prices." + m.name(), 0);
    }

    private int getBatchSize(Material m) {
        return plugin.getConfig().getInt("shop.sell-batch-prices." + m.name(), 0);
    }

    private String fmtName(Material m) {
        return m.name().replace('_', ' ').toLowerCase();
    }
}
