package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.commands.ShopCommand;
import org.bukkit.NamespacedKey;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ShopListener implements Listener {

    private final PHSurvival plugin;

    public ShopListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!title.contains("Pink Horizon") || !title.contains("Shop")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        NamespacedKey upgradeKey = new NamespacedKey(plugin, ShopCommand.UPGRADE_KEY);
        NamespacedKey priceKey   = new NamespacedKey(plugin, "shop_price");

        String upgrade = meta.getPersistentDataContainer().get(upgradeKey, PersistentDataType.STRING);
        Integer price  = meta.getPersistentDataContainer().get(priceKey,   PersistentDataType.INTEGER);
        if (upgrade == null || price == null) return;

        switch (upgrade) {
            case ShopCommand.FLY_10   -> handleFly(player, price, 10 * 60_000L);
            case ShopCommand.FLY_30   -> handleFly(player, price, 30 * 60_000L);
            case ShopCommand.FLY_60   -> handleFly(player, price, 60 * 60_000L);
            case ShopCommand.FLY_PERM -> handlePermFly(player, price);
            case ShopCommand.KI_10    -> handleTempKI(player, price, 10 * 60_000L);
            case ShopCommand.KI_30    -> handleTempKI(player, price, 30 * 60_000L);
            case ShopCommand.KI_60    -> handleTempKI(player, price, 60 * 60_000L);
            case ShopCommand.KI_PERM  -> handlePermKI(player, price);
            case ShopCommand.CLAIMS_5 -> handleClaims(player, price, 5);
            case ShopCommand.CLAIMS_15-> handleClaims(player, price, 15);
        }

        plugin.getShopCommand().openShop(player);
    }

    private void handleFly(Player player, int price, long durationMs) {
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§cNicht genug Coins! Preis: §f" + price);
            return;
        }
        plugin.getUpgradeManager().grantFly(player, durationMs);
        long mins = durationMs / 60_000;
        player.sendMessage("§aFly für §f" + mins + " §aMinuten freigeschaltet!");
    }

    private void handleTempKI(Player player, int price, long durationMs) {
        if (plugin.getUpgradeManager().hasPermKI(player.getUniqueId())) {
            player.sendMessage("§eDu hast bereits dauerhaftes KeepInventory!");
            return;
        }
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§cNicht genug Coins! Preis: §f" + price);
            return;
        }
        plugin.getUpgradeManager().grantTempKI(player.getUniqueId(), durationMs);
        long mins = durationMs / 60_000;
        player.sendMessage("§aKeepInventory für §f" + mins + " §aMinuten aktiviert!");
    }

    private void handlePermFly(Player player, int price) {
        if (plugin.getUpgradeManager().hasPermFly(player.getUniqueId())) {
            player.sendMessage("§cDu hast dauerhaften Fly bereits!");
            return;
        }
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§cNicht genug Coins! Preis: §f" + price);
            return;
        }
        plugin.getUpgradeManager().givePermFly(player);
        player.sendMessage("§aFly §ldauerhaft§r§a freigeschaltet!");
    }

    private void handlePermKI(Player player, int price) {
        if (plugin.getUpgradeManager().hasPermKI(player.getUniqueId())) {
            player.sendMessage("§cDu hast dauerhaftes KeepInventory bereits!");
            return;
        }
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§cNicht genug Coins! Preis: §f" + price);
            return;
        }
        plugin.getUpgradeManager().givePermKI(player.getUniqueId());
        player.sendMessage("§aKeepInventory §ldauerhaft§r§a freigeschaltet!");
    }

    private void handleClaims(Player player, int ignoredPrice, int amount) {
        UUID uuid = player.getUniqueId();
        int current = plugin.getUpgradeManager().getExtraClaims(uuid);
        if (current >= 50) {
            player.sendMessage("§cMaximum an extra Claim-Slots erreicht (+50)!");
            return;
        }
        // Preis dynamisch berechnen (ignoriert PDC-Preis)
        long basePrice = amount == 5 ? 1_500L : 4_000L;
        long price = plugin.getUpgradeManager().getClaimPrice(uuid, basePrice);

        int actual = Math.min(amount, 50 - current);
        if (!plugin.getEconomyManager().withdraw(uuid, price)) {
            player.sendMessage("§cNicht genug Coins! Preis: §f" + price);
            return;
        }
        plugin.getUpgradeManager().addExtraClaims(uuid, actual);
        plugin.getUpgradeManager().incrementClaimPurchases(uuid);

        long nextPrice = plugin.getUpgradeManager().getClaimPrice(uuid, basePrice);
        player.sendMessage("§a+" + actual + " Claim-Slots! §7(Gesamt extra: §a+" + (current + actual) + "§7)");
        player.sendMessage("§7Nächster Kauf dieser Größe kostet: §c" + nextPrice + " §7Coins");
    }
}
