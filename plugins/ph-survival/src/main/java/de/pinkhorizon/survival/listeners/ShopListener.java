package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.commands.ShopCommand;
import org.bukkit.Material;
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
            case ShopCommand.CLAIMS_5      -> handleClaims(player, price, 5);
            case ShopCommand.CLAIMS_15     -> handleClaims(player, price, 15);
            case ShopCommand.VILLAGER_EGG  -> handleVillagerEgg(player, price);
            case ShopCommand.HOME_SLOT     -> handleHomeSlot(player);
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

    private void handleHomeSlot(Player player) {
        UUID uuid = player.getUniqueId();
        int current = plugin.getUpgradeManager().getExtraHomes(uuid);
        if (current >= 10) {
            player.sendMessage("§cDu hast bereits das Maximum an extra Home-Slots (+10)!");
            return;
        }
        long price = plugin.getUpgradeManager().getNextHomePrice(uuid);
        if (!plugin.getEconomyManager().withdraw(uuid, price)) {
            player.sendMessage("§cNicht genug Coins! Preis: §f" + price);
            return;
        }
        plugin.getUpgradeManager().addExtraHome(uuid);
        int newTotal = plugin.getRankManager().getMaxHomes(uuid);
        player.sendMessage("§a+1 Home-Slot! §7Du kannst jetzt §f" + newTotal + " §7Homes setzen.");
        if (current + 1 < 10) {
            long nextPrice = plugin.getUpgradeManager().getNextHomePrice(uuid);
            player.sendMessage("§7Nächster Slot kostet: §c" + nextPrice + " §7Coins");
        }
    }

    private void handleVillagerEgg(Player player, int price) {
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§cNicht genug Coins! Preis: §f" + price);
            return;
        }
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG, 1);
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), egg);
            player.sendMessage("§aVillager-Ei gekauft! §7(Inventar voll – Item auf dem Boden)");
        } else {
            player.getInventory().addItem(egg);
            player.sendMessage("§aVillager-Ei für §f350.000 Coins §agekauft!");
        }
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
