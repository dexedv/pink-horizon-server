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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChestShopListener implements Listener {

    public static final String KEY_OWNER        = "shop_owner";
    public static final String KEY_ITEM         = "shop_item";
    public static final String KEY_AMOUNT       = "shop_amount";
    public static final String KEY_BUY          = "shop_buy";
    public static final String KEY_SELL         = "shop_sell";
    public static final String KEY_BALANCE      = "shop_balance";
    public static final String KEY_USE_SHOP_BAL = "shop_use_shop_bal";

    private final PHSurvival plugin;
    private final NamespacedKey ownerKey, itemKey, amountKey, buyKey, sellKey,
                                balanceKey, useShopBalKey;

    public ChestShopListener(PHSurvival plugin) {
        this.plugin        = plugin;
        this.ownerKey      = new NamespacedKey(plugin, KEY_OWNER);
        this.itemKey       = new NamespacedKey(plugin, KEY_ITEM);
        this.amountKey     = new NamespacedKey(plugin, KEY_AMOUNT);
        this.buyKey        = new NamespacedKey(plugin, KEY_BUY);
        this.sellKey       = new NamespacedKey(plugin, KEY_SELL);
        this.balanceKey    = new NamespacedKey(plugin, KEY_BALANCE);
        this.useShopBalKey = new NamespacedKey(plugin, KEY_USE_SHOP_BAL);
    }

    // ── Klick auf Shop-Truhe ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Chest chest = (Chest) block.getState();
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;

        event.setCancelled(true);
        Player player  = event.getPlayer();
        UUID   owner   = UUID.fromString(chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));

        // Besitzer + Shift → Settings-GUI
        if (player.getUniqueId().equals(owner) && player.isSneaking()) {
            openSettingsGUI(player, block);
            return;
        }

        // Alle anderen → Kunden-GUI
        Material itemType  = Material.valueOf(chest.getPersistentDataContainer().get(itemKey,   PersistentDataType.STRING));
        int      amount    = chest.getPersistentDataContainer().get(amountKey, PersistentDataType.INTEGER);
        long     buyPrice  = chest.getPersistentDataContainer().get(buyKey,    PersistentDataType.LONG);
        long     sellPrice = chest.getPersistentDataContainer().get(sellKey,   PersistentDataType.LONG);
        String   ownerName = Bukkit.getOfflinePlayer(owner).getName();

        openShopGUI(player, block, itemType, amount, buyPrice, sellPrice, ownerName, owner);
    }

    // ── Kunden-GUI ──────────────────────────────────────────────────────

    private void openShopGUI(Player player, Block block, Material item, int amount,
                              long buyPrice, long sellPrice, String ownerName, UUID ownerUUID) {
        ShopHolder holder = new ShopHolder(block, item, amount, buyPrice, sellPrice, ownerUUID);
        Inventory  gui    = Bukkit.createInventory(holder, 9, Component.text("§6[Shop] §f" + ownerName));

        if (buyPrice > 0) {
            gui.setItem(2, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a§lKAUFEN",
                List.of("§7" + amount + "x §f" + formatName(item),
                        "§7Preis: §6" + buyPrice + " §7Coins")));
        }

        ItemStack display = new ItemStack(item, Math.min(amount, 64));
        ItemMeta dm = display.getItemMeta();
        dm.displayName(Component.text(formatName(item), TextColor.color(0xFFD700)));
        dm.lore(List.of(
            Component.text("§7Menge: §f" + amount),
            buyPrice  > 0 ? Component.text("§7Kaufen: §f"    + buyPrice  + " Coins")
                          : Component.text("§8Kaufen: —"),
            sellPrice > 0 ? Component.text("§7Verkaufen: §f" + sellPrice + " Coins")
                          : Component.text("§8Verkaufen: —")
        ));
        display.setItemMeta(dm);
        gui.setItem(4, display);

        if (sellPrice > 0) {
            gui.setItem(6, makeItem(Material.RED_STAINED_GLASS_PANE, "§c§lVERKAUFEN",
                List.of("§7" + amount + "x §f" + formatName(item),
                        "§7Erlös: §6" + sellPrice + " §7Coins")));
        }

        player.openInventory(gui);
    }

    // ── Settings-GUI ────────────────────────────────────────────────────

    private void openSettingsGUI(Player player, Block block) {
        Chest   chest   = (Chest) block.getState();
        long    balance = getShopBalance(chest);
        boolean useShop = getUseShopBalance(chest);

        SettingsHolder holder = new SettingsHolder(block);
        Inventory gui = Bukkit.createInventory(holder, 27,
            Component.text("§6⚙ Shop Einstellungen"));

        // Filler
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of());
        for (int i = 0; i < 27; i++) gui.setItem(i, filler);

        // Shop-Kasse Anzeige (Mitte oben, Slot 4)
        gui.setItem(4, makeItem(Material.GOLD_INGOT, "§6§lShop-Kasse",
            List.of("§7Guthaben: §6§l" + balance + " §7Coins",
                    "",
                    "§7Zahlungsweise beim Ankauf:",
                    useShop  ? "§a● §aShop-Kasse"              : "§8○ §8Shop-Kasse",
                    !useShop ? "§a● §aPersönlich (Besitzer)"   : "§8○ §8Persönlich (Besitzer)")));

        // Einzahlen (Slots 1, 2, 3)
        gui.setItem(1, makeItem(Material.LIME_CONCRETE,   "§a§l+ 10 Coins",
            List.of("§7Aus deinem Konto einzahlen", "§7Dein Konto: §f" + plugin.getEconomyManager().getBalance(player.getUniqueId()) + " Coins")));
        gui.setItem(2, makeItem(Material.LIME_CONCRETE,   "§a§l+ 100 Coins",
            List.of("§7Aus deinem Konto einzahlen")));
        gui.setItem(3, makeItem(Material.LIME_CONCRETE,   "§a§l+ 1.000 Coins",
            List.of("§7Aus deinem Konto einzahlen")));

        // Auszahlen (Slots 5, 6, 7)
        gui.setItem(5, makeItem(Material.RED_CONCRETE, "§c§l- 1.000 Coins",
            List.of("§7Auf dein Konto auszahlen")));
        gui.setItem(6, makeItem(Material.RED_CONCRETE, "§c§l- 100 Coins",
            List.of("§7Auf dein Konto auszahlen")));
        gui.setItem(7, makeItem(Material.RED_CONCRETE, "§c§l- 10 Coins",
            List.of("§7Auf dein Konto auszahlen")));

        // Zahlungsweise umschalten (Slot 13)
        gui.setItem(13, makeItem(Material.COMPARATOR, "§e§lZahlungsweise umschalten",
            List.of(useShop  ? "§a▶ §aShop-Kasse (aktiv)"            : "§7  §7Shop-Kasse",
                    !useShop ? "§a▶ §aPersönlich / Besitzer (aktiv)"  : "§7  §7Persönlich / Besitzer",
                    "",
                    "§7Klicken zum Wechseln")));

        // Truhe öffnen (Slot 11)
        gui.setItem(11, makeItem(Material.CHEST, "§f§lTruhe öffnen",
            List.of("§7Items einlegen oder entnehmen")));

        // Schließen (Slot 22)
        gui.setItem(22, makeItem(Material.BARRIER, "§c§lSchließen", List.of()));

        player.openInventory(gui);
    }

    // ── GUI-Klick-Handler ───────────────────────────────────────────────

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);

        if (event.getInventory().getHolder() instanceof ShopHolder holder) {
            handleShopClick(player, event.getRawSlot(), holder);
        } else if (event.getInventory().getHolder() instanceof SettingsHolder holder) {
            handleSettingsClick(player, event.getRawSlot(), holder);
        }
    }

    private void handleShopClick(Player player, int slot, ShopHolder h) {
        if (h.block.getType() != Material.CHEST) {
            player.sendMessage("§cDer Shop existiert nicht mehr!");
            player.closeInventory();
            return;
        }
        if (slot == 2 && h.buyPrice  > 0) executeBuy (player, h.block, h);
        if (slot == 6 && h.sellPrice > 0) executeSell(player, h.block, h);
    }

    private void handleSettingsClick(Player player, int slot, SettingsHolder h) {
        if (h.block.getType() != Material.CHEST) { player.closeInventory(); return; }
        switch (slot) {
            case 1  -> doDeposit (player, h.block, 10);
            case 2  -> doDeposit (player, h.block, 100);
            case 3  -> doDeposit (player, h.block, 1000);
            case 5  -> doWithdraw(player, h.block, 1000);
            case 6  -> doWithdraw(player, h.block, 100);
            case 7  -> doWithdraw(player, h.block, 10);
            case 11 -> { player.closeInventory();
                         player.openInventory(((Chest) h.block.getState()).getInventory()); }
            case 13 -> doModeToggle(player, h.block);
            case 22 -> player.closeInventory();
        }
    }

    // ── Kaufen ──────────────────────────────────────────────────────────

    private void executeBuy(Player player, Block block, ShopHolder h) {
        Chest chest = (Chest) block.getState();
        if (countItems(chest.getInventory(), h.item) < h.amount) {
            player.sendMessage("§cNicht auf Lager!");
            player.closeInventory();
            return;
        }
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), h.buyPrice)) {
            player.sendMessage("§cNicht genug Coins! (§f" + h.buyPrice + "§c benötigt)");
            return;
        }
        // Inventar direkt ändern – KEIN chest.update() danach!
        removeItems(chest.getInventory(), h.item, h.amount);
        player.getInventory().addItem(new ItemStack(h.item, h.amount))
            .values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        plugin.getEconomyManager().deposit(h.ownerUUID, h.buyPrice);
        player.sendMessage("§aGekauft: §f" + h.amount + "x " + formatName(h.item)
            + " §afür §6" + h.buyPrice + " §aCoins");
        player.closeInventory();
    }

    // ── Verkaufen ───────────────────────────────────────────────────────

    private void executeSell(Player player, Block block, ShopHolder h) {
        Chest chest = (Chest) block.getState();
        if (countItems(player.getInventory(), h.item) < h.amount) {
            player.sendMessage("§cDu hast nicht genug §f" + formatName(h.item)
                + "§c! (§f" + h.amount + "§c benötigt)");
            return;
        }
        if (chest.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cDer Shop ist voll!");
            return;
        }

        // Zahlung prüfen und abziehen
        boolean useShopBal = getUseShopBalance(chest);
        if (useShopBal) {
            long shopBal = getShopBalance(chest);
            if (shopBal < h.sellPrice) {
                player.sendMessage("§cDie Shop-Kasse hat nicht genug Coins! (§f" + shopBal + "§c verfügbar)");
                return;
            }
            // PDC-Änderung VOR Inventar-Änderung → chest.update() hier sicher
            chest.getPersistentDataContainer().set(balanceKey, PersistentDataType.LONG, shopBal - h.sellPrice);
            chest.update();
        } else {
            if (!plugin.getEconomyManager().withdraw(h.ownerUUID, h.sellPrice)) {
                player.sendMessage("§cDer Shop-Besitzer hat nicht genug Coins!");
                return;
            }
        }

        // Inventar-Änderungen – KEIN chest.update() danach!
        removeItems(player.getInventory(), h.item, h.amount);
        chest.getInventory().addItem(new ItemStack(h.item, h.amount));
        plugin.getEconomyManager().deposit(player.getUniqueId(), h.sellPrice);
        player.sendMessage("§aVerkauft: §f" + h.amount + "x " + formatName(h.item)
            + " §afür §6" + h.sellPrice + " §aCoins");
        player.closeInventory();
    }

    // ── Settings-Aktionen ───────────────────────────────────────────────

    private void doDeposit(Player player, Block block, long amount) {
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), amount)) {
            player.sendMessage("§cNicht genug Coins auf deinem Konto!");
            openSettingsGUI(player, block);
            return;
        }
        Chest chest  = (Chest) block.getState();
        long  newBal = getShopBalance(chest) + amount;
        chest.getPersistentDataContainer().set(balanceKey, PersistentDataType.LONG, newBal);
        chest.update();
        player.sendMessage("§a+" + amount + " Coins in Kasse eingezahlt §7(Kasse: §f" + newBal + "§7)");
        openSettingsGUI(player, block);
    }

    private void doWithdraw(Player player, Block block, long amount) {
        Chest chest = (Chest) block.getState();
        long  bal   = getShopBalance(chest);
        if (bal < amount) {
            player.sendMessage("§cNur §f" + bal + "§c Coins in der Kasse!");
            openSettingsGUI(player, block);
            return;
        }
        chest.getPersistentDataContainer().set(balanceKey, PersistentDataType.LONG, bal - amount);
        chest.update();
        plugin.getEconomyManager().deposit(player.getUniqueId(), amount);
        player.sendMessage("§a-" + amount + " Coins ausgezahlt §7(Kasse: §f" + (bal - amount) + "§7)");
        openSettingsGUI(player, block);
    }

    private void doModeToggle(Player player, Block block) {
        Chest   chest   = (Chest) block.getState();
        boolean newMode = !getUseShopBalance(chest);
        chest.getPersistentDataContainer().set(useShopBalKey, PersistentDataType.INTEGER, newMode ? 1 : 0);
        chest.update();
        player.sendMessage("§7Zahlungsweise: " + (newMode ? "§a§lShop-Kasse" : "§e§lPersönlich (Besitzer)"));
        openSettingsGUI(player, block);
    }

    // ── Shop-Truhe abbauen ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block  block = event.getBlock();
        if (block.getType() != Material.CHEST) return;
        Chest  chest = (Chest) block.getState();
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;

        Player player = event.getPlayer();
        UUID   owner  = UUID.fromString(chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));
        if (!player.getUniqueId().equals(owner) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage("§cDas ist ein fremder Shop!");
            return;
        }
        // Kassen-Guthaben zurückgeben
        long balance = getShopBalance(chest);
        if (balance > 0) {
            plugin.getEconomyManager().deposit(owner, balance);
            player.sendMessage("§7Shop-Kasse ausgezahlt: §6" + balance + " §7Coins");
        }
        chest.getPersistentDataContainer().remove(ownerKey);
        chest.update();
        plugin.getHologramManager().remove(shopHoloKey(block));
    }

    // ── PDC Hilfsmethoden ───────────────────────────────────────────────

    private long getShopBalance(Chest chest) {
        if (!chest.getPersistentDataContainer().has(balanceKey)) return 0L;
        return chest.getPersistentDataContainer().get(balanceKey, PersistentDataType.LONG);
    }

    private boolean getUseShopBalance(Chest chest) {
        if (!chest.getPersistentDataContainer().has(useShopBalKey)) return false;
        return chest.getPersistentDataContainer().get(useShopBalKey, PersistentDataType.INTEGER) == 1;
    }

    // ── Hologramm-Hilfsmethoden ─────────────────────────────────────────

    public static String shopHoloKey(Block block) {
        return "shop_" + block.getWorld().getName()
            + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    public static List<String> shopHoloLines(String itemName, int amount, long buyPrice, long sellPrice) {
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>✦ Shop ✦</bold></gold>");
        lines.add("<white>" + amount + "x " + itemName + "</white>");
        if (buyPrice  > 0) lines.add("<green>Kaufen: <white>" + buyPrice  + " Coins</white></green>");
        if (sellPrice > 0) lines.add("<red>Verkaufen: <white>" + sellPrice + " Coins</white></red>");
        return lines;
    }

    // ── Item-Builder ────────────────────────────────────────────────────

    private ItemStack makeItem(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String l : loreLines) lore.add(Component.text(l));
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    // ── Inventar-Hilfsmethoden ──────────────────────────────────────────

    private int countItems(Inventory inv, Material mat) {
        int count = 0;
        for (ItemStack s : inv.getContents())
            if (s != null && s.getType() == mat) count += s.getAmount();
        return count;
    }

    private void removeItems(Inventory inv, Material mat, int amount) {
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

    public static String formatName(Material mat) {
        String s = mat.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Holders ─────────────────────────────────────────────────────────

    public static class ShopHolder implements InventoryHolder {
        final Block block; final Material item; final int amount;
        final long buyPrice, sellPrice; final UUID ownerUUID;

        ShopHolder(Block block, Material item, int amount, long buy, long sell, UUID ownerUUID) {
            this.block = block; this.item = item; this.amount = amount;
            this.buyPrice = buy; this.sellPrice = sell; this.ownerUUID = ownerUUID;
        }
        @Override public Inventory getInventory() { return null; }
    }

    public static class SettingsHolder implements InventoryHolder {
        final Block block;
        SettingsHolder(Block block) { this.block = block; }
        @Override public Inventory getInventory() { return null; }
    }
}
