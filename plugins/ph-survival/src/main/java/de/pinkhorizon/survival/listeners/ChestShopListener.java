package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class ChestShopListener implements Listener {

    // PDC keys (gesetzt durch CreateShopCommand)
    public static final String KEY_OWNER  = "shop_owner";
    public static final String KEY_ITEM   = "shop_item";
    public static final String KEY_AMOUNT = "shop_amount";
    public static final String KEY_BUY    = "shop_buy";
    public static final String KEY_SELL   = "shop_sell";

    private final PHSurvival plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey amountKey;
    private final NamespacedKey buyKey;
    private final NamespacedKey sellKey;

    public ChestShopListener(PHSurvival plugin) {
        this.plugin    = plugin;
        this.ownerKey  = new NamespacedKey(plugin, KEY_OWNER);
        this.itemKey   = new NamespacedKey(plugin, KEY_ITEM);
        this.amountKey = new NamespacedKey(plugin, KEY_AMOUNT);
        this.buyKey    = new NamespacedKey(plugin, KEY_BUY);
        this.sellKey   = new NamespacedKey(plugin, KEY_SELL);
    }

    // ── Klick auf Shop-Truhe ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Chest chest = (Chest) block.getState();
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;

        Player player = event.getPlayer();
        UUID owner = UUID.fromString(chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));

        // Eigentümer & Shift → normale Truhe öffnen für Bestückung
        if (player.getUniqueId().equals(owner) && player.isSneaking()) return;

        event.setCancelled(true);

        Material itemType = Material.valueOf(chest.getPersistentDataContainer().get(itemKey,   PersistentDataType.STRING));
        int      amount   = chest.getPersistentDataContainer().get(amountKey, PersistentDataType.INTEGER);
        long     buyPrice = chest.getPersistentDataContainer().get(buyKey,    PersistentDataType.LONG);
        long     sellPrice= chest.getPersistentDataContainer().get(sellKey,   PersistentDataType.LONG);
        String   ownerName= Bukkit.getOfflinePlayer(owner).getName();

        openShopGUI(player, block.getLocation(), itemType, amount, buyPrice, sellPrice, ownerName);
    }

    // ── Shop-GUI öffnen ─────────────────────────────────────────────────

    private void openShopGUI(Player player, org.bukkit.Location loc,
                              Material item, int amount, long buyPrice, long sellPrice, String ownerName) {
        ShopHolder holder = new ShopHolder(loc, item, amount, buyPrice, sellPrice);
        Inventory gui = Bukkit.createInventory(holder, 9,
            Component.text("§6[Shop] §f" + ownerName));

        // Slot 2: Kaufen
        if (buyPrice > 0) {
            ItemStack buyBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta m = buyBtn.getItemMeta();
            m.displayName(Component.text("§a§lKAUFEN", NamedTextColor.GREEN));
            m.lore(List.of(
                Component.text("§7" + amount + "x §f" + formatName(item)),
                Component.text("§7Preis: §f" + buyPrice + " §7Coins")
            ));
            buyBtn.setItemMeta(m);
            gui.setItem(2, buyBtn);
        }

        // Slot 4: Item-Info
        ItemStack display = new ItemStack(item, amount);
        ItemMeta dm = display.getItemMeta();
        dm.displayName(Component.text("§e" + formatName(item), TextColor.color(0xFFD700)));
        dm.lore(List.of(
            Component.text("§7Menge: §f" + amount),
            buyPrice  > 0 ? Component.text("§7Kaufen: §f" + buyPrice  + " Coins") : Component.text("§8Kaufen: deaktiviert"),
            sellPrice > 0 ? Component.text("§7Verkaufen: §f" + sellPrice + " Coins") : Component.text("§8Verkaufen: deaktiviert")
        ));
        display.setItemMeta(dm);
        gui.setItem(4, display);

        // Slot 6: Verkaufen
        if (sellPrice > 0) {
            ItemStack sellBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta sm = sellBtn.getItemMeta();
            sm.displayName(Component.text("§c§lVERKAUFEN", NamedTextColor.RED));
            sm.lore(List.of(
                Component.text("§7" + amount + "x §f" + formatName(item)),
                Component.text("§7Erlös: §f" + sellPrice + " §7Coins")
            ));
            sellBtn.setItemMeta(sm);
            gui.setItem(6, sellBtn);
        }

        player.openInventory(gui);
    }

    // ── GUI-Klick ───────────────────────────────────────────────────────

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Block block = holder.location.getBlock();
        if (block.getType() != Material.CHEST) {
            player.sendMessage("§cDer Shop existiert nicht mehr!");
            player.closeInventory();
            return;
        }
        Chest chest = (Chest) block.getState();

        int slot = event.getRawSlot();
        if (slot == 2 && holder.buyPrice > 0) executeBuy(player, chest, holder);
        if (slot == 6 && holder.sellPrice > 0) executeSell(player, chest, holder);
    }

    // ── Kaufen ──────────────────────────────────────────────────────────

    private void executeBuy(Player player, Chest chest, ShopHolder h) {
        // Truhe hat genug Items?
        int available = countItems(chest.getInventory(), h.item);
        if (available < h.amount) {
            player.sendMessage("§cNicht auf Lager!");
            player.closeInventory();
            return;
        }
        // Spieler hat genug Coins?
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), h.buyPrice)) {
            player.sendMessage("§cNicht genug Coins! (§f" + h.buyPrice + "§c benötigt)");
            return;
        }
        // Items übertragen
        removeItems(chest.getInventory(), h.item, h.amount);
        chest.update();
        player.getInventory().addItem(new ItemStack(h.item, h.amount))
            .values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));

        // Verkäufer bezahlen
        UUID ownerUUID = UUID.fromString(chest.getPersistentDataContainer().get(
            new NamespacedKey(plugin, KEY_OWNER), PersistentDataType.STRING));
        plugin.getEconomyManager().deposit(ownerUUID, h.buyPrice);

        player.sendMessage("§aGekauft: §f" + h.amount + "x " + formatName(h.item) + " §afür §f" + h.buyPrice + " §aCoins");
        player.closeInventory();
    }

    // ── Verkaufen ───────────────────────────────────────────────────────

    private void executeSell(Player player, Chest chest, ShopHolder h) {
        // Spieler hat Items?
        int has = countItems(player.getInventory(), h.item);
        if (has < h.amount) {
            player.sendMessage("§cDu hast nicht genug §f" + formatName(h.item) + "§c! (§f" + h.amount + "§c benötigt)");
            return;
        }
        // Truhe hat Platz?
        if (chest.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cDer Shop ist voll – kann nichts annehmen!");
            return;
        }
        // Shop-Besitzer muss die Coins für den Kauf haben
        UUID ownerUUID = UUID.fromString(chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));
        if (!plugin.getEconomyManager().withdraw(ownerUUID, h.sellPrice)) {
            player.sendMessage("§cDer Shop-Besitzer hat nicht genug Coins!");
            return;
        }
        removeItems(player.getInventory(), h.item, h.amount);
        chest.getInventory().addItem(new ItemStack(h.item, h.amount));
        chest.update();
        plugin.getEconomyManager().deposit(player.getUniqueId(), h.sellPrice);

        player.sendMessage("§aVerkauft: §f" + h.amount + "x " + formatName(h.item) + " §afür §f" + h.sellPrice + " §aCoins");
        player.closeInventory();
    }

    // ── Shop-Truhe abbauen ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;
        Chest chest = (Chest) block.getState();
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;

        Player player = event.getPlayer();
        UUID owner = UUID.fromString(chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));
        if (!player.getUniqueId().equals(owner) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage("§cDas ist ein fremder Shop!");
            return;
        }
        // Shop-PDC und Hologramm entfernen (Truhe wird normal abgebaut)
        chest.getPersistentDataContainer().remove(ownerKey);
        chest.update();
        plugin.getHologramManager().remove(shopHoloKey(block));
    }

    // ── Hologram-Hilfsmethoden (auch von CreateShopCommand genutzt) ──────

    public static String shopHoloKey(org.bukkit.block.Block block) {
        return "shop_" + block.getWorld().getName()
            + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    public static java.util.List<String> shopHoloLines(String itemName, int amount,
                                                        long buyPrice, long sellPrice) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("<gold><bold>✦ Shop ✦</bold></gold>");
        lines.add("<white>" + amount + "x " + itemName + "</white>");
        if (buyPrice  > 0) lines.add("<green>Kaufen: <white>" + buyPrice  + " Coins</white></green>");
        if (sellPrice > 0) lines.add("<red>Verkaufen: <white>" + sellPrice + " Coins</white></red>");
        return lines;
    }

    // ── Hilfsmethoden ───────────────────────────────────────────────────

    private int countItems(org.bukkit.inventory.Inventory inv, Material mat) {
        int count = 0;
        for (ItemStack s : inv.getContents()) if (s != null && s.getType() == mat) count += s.getAmount();
        return count;
    }

    private void removeItems(org.bukkit.inventory.Inventory inv, Material mat, int amount) {
        int left = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            if (contents[i] == null || contents[i].getType() != mat) continue;
            if (contents[i].getAmount() <= left) {
                left -= contents[i].getAmount();
                contents[i] = null;
            } else {
                contents[i].setAmount(contents[i].getAmount() - left);
                left = 0;
            }
        }
        inv.setContents(contents);
    }

    private String formatName(Material mat) {
        String name = mat.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // ── Shop-GUI-Holder ─────────────────────────────────────────────────

    public static class ShopHolder implements InventoryHolder {
        final org.bukkit.Location location;
        final Material item;
        final int amount;
        final long buyPrice, sellPrice;
        private Inventory inv;

        ShopHolder(org.bukkit.Location loc, Material item, int amount, long buy, long sell) {
            this.location = loc; this.item = item; this.amount = amount;
            this.buyPrice = buy; this.sellPrice = sell;
        }

        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory inv) { this.inv = inv; }
    }
}
