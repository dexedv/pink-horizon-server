package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.listeners.ChestShopListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class CreateShopCommand implements CommandExecutor {

    private final PHSurvival plugin;
    private final NamespacedKey ownerKey, itemKey, amountKey, buyKey, sellKey;

    public CreateShopCommand(PHSurvival plugin) {
        this.plugin    = plugin;
        this.ownerKey  = new NamespacedKey(plugin, ChestShopListener.KEY_OWNER);
        this.itemKey   = new NamespacedKey(plugin, ChestShopListener.KEY_ITEM);
        this.amountKey = new NamespacedKey(plugin, ChestShopListener.KEY_AMOUNT);
        this.buyKey    = new NamespacedKey(plugin, ChestShopListener.KEY_BUY);
        this.sellKey   = new NamespacedKey(plugin, ChestShopListener.KEY_SELL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler!");
            return true;
        }

        // /removeshop
        if (label.equalsIgnoreCase("removeshop")) {
            Block target = getTargetChest(player);
            if (target == null) { player.sendMessage("§cSchau auf eine Truhe!"); return true; }
            Chest chest = (Chest) target.getState();
            if (!chest.getPersistentDataContainer().has(ownerKey)) {
                player.sendMessage("§cDas ist kein Shop!");
                return true;
            }
            String ownerStr = chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (!player.getUniqueId().toString().equals(ownerStr) && !player.isOp()) {
                player.sendMessage("§cDas ist nicht dein Shop!");
                return true;
            }
            chest.getPersistentDataContainer().remove(ownerKey);
            chest.customName(null);
            chest.update();
            player.sendMessage("§aShop entfernt.");
            return true;
        }

        // /createshop <amount> <buy_price> [sell_price]
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /createshop <Menge> <Kaufpreis> [Verkaufspreis]");
            player.sendMessage("§7Schau dabei auf die Truhe. Shift+Klick = Truhe befüllen.");
            return true;
        }

        Block target = getTargetChest(player);
        if (target == null) { player.sendMessage("§cSchau auf eine Truhe!"); return true; }
        Chest chest = (Chest) target.getState();

        // Bereits ein Shop?
        if (chest.getPersistentDataContainer().has(ownerKey)) {
            String ownerStr = chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (!player.getUniqueId().toString().equals(ownerStr) && !player.isOp()) {
                player.sendMessage("§cDiese Truhe gehört bereits einem anderen Shop!");
                return true;
            }
        }

        // Claim-Check: Spieler muss diese Truhe besitzen (oder OP)
        if (!player.isOp()) {
            org.bukkit.Chunk chunk = target.getChunk();
            if (plugin.getClaimManager().isClaimed(chunk)
                    && !plugin.getClaimManager().isTrusted(chunk, player.getUniqueId())) {
                player.sendMessage("§cDu kannst hier keinen Shop erstellen (fremder Claim)!");
                return true;
            }
        }

        // Was ist in der Truhe?
        Material shopItem = null;
        for (org.bukkit.inventory.ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                shopItem = item.getType();
                break;
            }
        }
        if (shopItem == null) {
            player.sendMessage("§cTruhe ist leer! Lege zuerst das zu verkaufende Item rein.");
            return true;
        }

        int amount;
        long buyPrice, sellPrice = 0;
        try {
            amount   = Integer.parseInt(args[0]);
            buyPrice = Long.parseLong(args[1]);
            if (args.length >= 3) sellPrice = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cUngültige Zahlen!");
            return true;
        }

        if (amount < 1 || buyPrice < 0 || sellPrice < 0) {
            player.sendMessage("§cUngültige Werte!");
            return true;
        }

        // PDC setzen
        chest.getPersistentDataContainer().set(ownerKey,  PersistentDataType.STRING,  player.getUniqueId().toString());
        chest.getPersistentDataContainer().set(itemKey,   PersistentDataType.STRING,  shopItem.name());
        chest.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, amount);
        chest.getPersistentDataContainer().set(buyKey,    PersistentDataType.LONG,    buyPrice);
        chest.getPersistentDataContainer().set(sellKey,   PersistentDataType.LONG,    sellPrice);

        String itemName = shopItem.name().replace('_', ' ').toLowerCase();
        chest.customName(Component.text("§6[Shop] §f" + amount + "x " + itemName
            + " §8| §aB:" + buyPrice + (sellPrice > 0 ? " §cS:" + sellPrice : "")));
        chest.update();

        player.sendMessage("§aShop erstellt! §7(" + amount + "x " + itemName
            + " | Kaufen: §f" + buyPrice + " §7| Verkaufen: §f" + sellPrice + "§7)");
        player.sendMessage("§7Bestücke die Truhe mit §fShift+Klick§7.");
        return true;
    }

    private Block getTargetChest(Player player) {
        Block b = player.getTargetBlockExact(5);
        if (b == null || b.getType() != Material.CHEST) return null;
        return b;
    }
}
