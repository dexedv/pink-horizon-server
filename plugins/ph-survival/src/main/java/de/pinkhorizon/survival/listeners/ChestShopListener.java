package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ChestShopListener implements Listener {

    // PDC keys
    public static final String KEY_OWNER        = "shop_owner";
    public static final String KEY_ITEM         = "shop_item";
    public static final String KEY_AMOUNT       = "shop_amount";
    public static final String KEY_BUY          = "shop_buy";
    public static final String KEY_SELL         = "shop_sell";
    public static final String KEY_BUY_ON       = "shop_buy_on";
    public static final String KEY_SELL_ON      = "shop_sell_on";
    public static final String KEY_BALANCE      = "shop_balance";
    public static final String KEY_USE_SHOP_BAL = "shop_use_shop_bal";

    private final PHSurvival plugin;
    private final NamespacedKey ownerKey, itemKey, amountKey,
                                buyKey, sellKey, buyOnKey, sellOnKey,
                                balanceKey, useShopBalKey;

    private final Set<UUID> pendingCreate = new HashSet<>();
    private final Set<UUID> pendingRemove = new HashSet<>();

    public ChestShopListener(PHSurvival plugin) {
        this.plugin        = plugin;
        this.ownerKey      = new NamespacedKey(plugin, KEY_OWNER);
        this.itemKey       = new NamespacedKey(plugin, KEY_ITEM);
        this.amountKey     = new NamespacedKey(plugin, KEY_AMOUNT);
        this.buyKey        = new NamespacedKey(plugin, KEY_BUY);
        this.sellKey       = new NamespacedKey(plugin, KEY_SELL);
        this.buyOnKey      = new NamespacedKey(plugin, KEY_BUY_ON);
        this.sellOnKey     = new NamespacedKey(plugin, KEY_SELL_ON);
        this.balanceKey    = new NamespacedKey(plugin, KEY_BALANCE);
        this.useShopBalKey = new NamespacedKey(plugin, KEY_USE_SHOP_BAL);
    }

    public void addPendingCreate(UUID uuid) { pendingCreate.add(uuid); }

    public void startPendingRemove(Player player) {
        pendingRemove.add(player.getUniqueId());
        player.sendMessage("§cKlicke auf deinen Shop um ihn zu entfernen.");
    }

    // ── Klick auf Glas / Truhe ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Shop erstellen (pending)
        if (pendingCreate.contains(player.getUniqueId())) {
            if (block.getType() != Material.CHEST) {
                player.sendMessage("§cDas ist keine Truhe!");
                return;
            }
            pendingCreate.remove(player.getUniqueId());
            event.setCancelled(true);
            createShop(player, block);
            return;
        }

        if (block.getType() != Material.WHITE_STAINED_GLASS) return;

        Block chestBlock = block.getRelative(0, -1, 0);
        if (chestBlock.getType() != Material.CHEST) return;
        Chest chest = (Chest) chestBlock.getState();
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;

        event.setCancelled(true);
        UUID owner = UUID.fromString(chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));

        // Shop entfernen (pending)
        if (pendingRemove.remove(player.getUniqueId())) {
            if (!player.getUniqueId().equals(owner) && !player.isOp()) {
                player.sendMessage("§cDas ist nicht dein Shop!");
                return;
            }
            removeShop(player, block, chestBlock, chest, owner);
            return;
        }

        // Besitzer → Settings
        if (player.getUniqueId().equals(owner)) {
            openSettingsGUI(player, chestBlock);
            return;
        }

        // Kunde → Shop-GUI
        String matStr = chest.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (matStr == null) return;
        Material itemType = Material.matchMaterial(matStr);
        if (itemType == null) return;

        int    amount    = getPdcInt(chest, amountKey,  1);
        long   buyPrice  = getPdc(chest, buyKey,   0L);
        long   sellPrice = getPdc(chest, sellKey,  0L);
        boolean buyOn    = getPdcInt(chest, buyOnKey,  0) == 1;
        boolean sellOn   = getPdcInt(chest, sellOnKey, 0) == 1;
        String ownerName = Bukkit.getOfflinePlayer(owner).getName();
        openShopGUI(player, chestBlock, itemType, amount, buyPrice, sellPrice, buyOn, sellOn, ownerName, owner);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakGlass(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.WHITE_STAINED_GLASS) return;
        Block chestBlock = block.getRelative(0, -1, 0);
        if (chestBlock.getType() != Material.CHEST) return;
        Chest chest = (Chest) chestBlock.getState();
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;

        Player player = event.getPlayer();
        UUID owner = UUID.fromString(chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));
        if (!player.getUniqueId().equals(owner) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage("§cDas ist ein fremder Shop!");
            return;
        }
        event.setCancelled(true);
        removeShop(player, block, chestBlock, chest, owner);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakChest(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;
        Chest chest = (Chest) block.getState();
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cEntferne zuerst das Shop-Glas darüber!");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingCreate.remove(event.getPlayer().getUniqueId());
        pendingRemove.remove(event.getPlayer().getUniqueId());
    }

    // ── Shop erstellen / entfernen ───────────────────────────────────────

    private void createShop(Player player, Block block) {
        Chest chest = (Chest) block.getState();
        if (chest.getPersistentDataContainer().has(ownerKey)) {
            player.sendMessage("§cDiese Truhe ist bereits ein Shop!");
            return;
        }
        if (!player.isOp()) {
            org.bukkit.Chunk chunk = block.getChunk();
            if (plugin.getClaimManager().isClaimed(chunk)
                    && !plugin.getClaimManager().isTrusted(chunk, player.getUniqueId())) {
                player.sendMessage("§cDu kannst hier keinen Shop erstellen (fremder Claim)!");
                return;
            }
        }
        Block glassBlock = block.getRelative(0, 1, 0);
        if (!glassBlock.getType().isAir()) {
            player.sendMessage("§cÜber der Truhe muss ein freier Block sein!");
            return;
        }

        Material shopItem = detectItem(chest);
        if (shopItem == null) shopItem = Material.STONE;

        chest.getPersistentDataContainer().set(ownerKey,      PersistentDataType.STRING,  player.getUniqueId().toString());
        chest.getPersistentDataContainer().set(itemKey,       PersistentDataType.STRING,  shopItem.name());
        chest.getPersistentDataContainer().set(amountKey,     PersistentDataType.INTEGER, 1);
        chest.getPersistentDataContainer().set(buyKey,        PersistentDataType.LONG,    10L);
        chest.getPersistentDataContainer().set(sellKey,       PersistentDataType.LONG,    5L);
        chest.getPersistentDataContainer().set(buyOnKey,      PersistentDataType.INTEGER, 0);
        chest.getPersistentDataContainer().set(sellOnKey,     PersistentDataType.INTEGER, 0);
        chest.getPersistentDataContainer().set(balanceKey,    PersistentDataType.LONG,    0L);
        chest.getPersistentDataContainer().set(useShopBalKey, PersistentDataType.INTEGER, 0);
        chest.update();

        glassBlock.setType(Material.WHITE_STAINED_GLASS);
        spawnItemDisplay(glassBlock, shopItem);
        updateHologram(block);

        player.sendMessage("§aShop erstellt! §7Aktiviere Kaufen/Verkaufen im GUI.");
        openSettingsGUI(player, block);
    }

    private void removeShop(Player player, Block glassBlock, Block chestBlock, Chest chest, UUID owner) {
        long balance = getPdc(chest, balanceKey, 0L);
        if (balance > 0) {
            plugin.getEconomyManager().deposit(owner, balance);
            player.sendMessage("§7Shop-Kasse ausgezahlt: §6" + balance + " §7Coins");
        }
        removeItemDisplay(glassBlock);
        glassBlock.setType(Material.AIR);
        chest.getPersistentDataContainer().remove(ownerKey);
        chest.update();
        plugin.getHologramManager().remove(shopHoloKey(chestBlock));
        player.sendMessage("§aShop entfernt.");
    }

    // ── Item-Display ─────────────────────────────────────────────────────

    private void spawnItemDisplay(Block glassBlock, Material item) {
        removeItemDisplay(glassBlock);
        Location center = glassBlock.getLocation().add(0.5, 0.2, 0.5);
        glassBlock.getWorld().spawn(center, org.bukkit.entity.ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(item));
            display.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
            display.setPersistent(true);
            display.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0, 0, 0),
                new org.joml.AxisAngle4f(0, 0, 0, 1),
                new org.joml.Vector3f(0.6f, 0.6f, 0.6f),
                new org.joml.AxisAngle4f(0, 0, 0, 1)));
            display.setCustomName("shop_display");
            display.setCustomNameVisible(false);
        });
    }

    private void removeItemDisplay(Block glassBlock) {
        Location center = glassBlock.getLocation().add(0.5, 0.5, 0.5);
        glassBlock.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8).stream()
            .filter(e -> e instanceof org.bukkit.entity.ItemDisplay
                && "shop_display".equals(e.getCustomName()))
            .forEach(org.bukkit.entity.Entity::remove);
    }

    private void updateItemDisplay(Block chestBlock, Material item) {
        Block glassBlock = chestBlock.getRelative(0, 1, 0);
        if (glassBlock.getType() == Material.WHITE_STAINED_GLASS)
            spawnItemDisplay(glassBlock, item);
    }

    // ── Kunden-GUI ──────────────────────────────────────────────────────

    private void openShopGUI(Player player, Block block, Material item, int amount,
                              long buyPrice, long sellPrice, boolean buyOn, boolean sellOn,
                              String ownerName, UUID ownerUUID) {
        ShopHolder holder = new ShopHolder(block, item, amount, buyPrice, sellPrice, buyOn, sellOn, ownerUUID);
        Inventory  gui    = Bukkit.createInventory(holder, 9,
            Component.text("§6[Shop] §f" + ownerName));

        if (buyOn && buyPrice > 0) {
            gui.setItem(2, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a§lKAUFEN",
                List.of("§7" + amount + "x §f" + formatName(item),
                        "§7Preis: §6" + buyPrice + " §7Coins")));
        }

        ItemStack display = new ItemStack(item, Math.min(amount, 64));
        ItemMeta dm = display.getItemMeta();
        dm.displayName(Component.text(formatName(item), TextColor.color(0xFFD700)));
        dm.lore(List.of(
            Component.text("§7Menge: §f" + amount),
            buyOn && buyPrice > 0
                ? Component.text("§7Kaufen: §f" + buyPrice + " Coins")
                : Component.text("§8Kaufen: deaktiviert"),
            sellOn && sellPrice > 0
                ? Component.text("§7Verkaufen: §f" + sellPrice + " Coins")
                : Component.text("§8Verkaufen: deaktiviert")
        ));
        display.setItemMeta(dm);
        gui.setItem(4, display);

        if (sellOn && sellPrice > 0) {
            gui.setItem(6, makeItem(Material.RED_STAINED_GLASS_PANE, "§c§lVERKAUFEN",
                List.of("§7" + amount + "x §f" + formatName(item),
                        "§7Erlös: §6" + sellPrice + " §7Coins")));
        }
        player.openInventory(gui);
    }

    // ── Settings-GUI (45 Slots) ──────────────────────────────────────────
    //
    //  Slot  0 : [KAUFEN AN/AUS]   1-3: -100/-10/-1   4: Kaufpreis-Info   5-7: +1/+10/+100
    //  Slot  9 : [VERKAUFEN AN/AUS] 10-12: -100/-10/-1  13: Verkaufspreis-Info  14-16: +1/+10/+100
    //  Slot 18-26: Menge / Item
    //  Slot 27-35: Shop-Kasse
    //  Slot 36-44: Aktionen

    private void openSettingsGUI(Player player, Block block) {
        Chest    chest     = (Chest) block.getState();
        long     buyPrice  = getPdc(chest, buyKey,    10L);
        long     sellPrice = getPdc(chest, sellKey,   5L);
        boolean  buyOn     = getPdcInt(chest, buyOnKey,   0) == 1;
        boolean  sellOn    = getPdcInt(chest, sellOnKey,  0) == 1;
        int      amount    = getPdcInt(chest, amountKey,  1);
        long     balance   = getPdc(chest, balanceKey, 0L);
        boolean  useShop   = getPdcInt(chest, useShopBalKey, 0) == 1;
        String   itemStr   = chest.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        Material item      = itemStr != null ? Material.matchMaterial(itemStr) : null;
        if (item == null) item = Material.STONE;

        boolean alreadyOpen = player.getOpenInventory().getTopInventory()
                                    .getHolder() instanceof SettingsHolder;
        Inventory gui;
        if (alreadyOpen) {
            gui = player.getOpenInventory().getTopInventory();
        } else {
            gui = Bukkit.createInventory(new SettingsHolder(block), 45,
                Component.text("§6⚙ Shop Einstellungen"));
        }

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of());
        for (int i = 0; i < 45; i++) gui.setItem(i, filler);

        // ── Reihe 0: Kaufen ──────────────────────────────────────────────
        gui.setItem(0, buyOn
            ? makeItem(Material.LIME_CONCRETE, "§a§l✔ KAUFEN AKTIV",
                List.of("§7Spieler können Items kaufen", "§7Klicken zum Deaktivieren"))
            : makeItem(Material.RED_CONCRETE,  "§c§l✘ KAUFEN INAKTIV",
                List.of("§7Spieler können keine Items kaufen", "§7Klicken zum Aktivieren")));
        gui.setItem(1, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 100", List.of("§7Kaufpreis")));
        gui.setItem(2, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 10",  List.of("§7Kaufpreis")));
        gui.setItem(3, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 1",   List.of("§7Kaufpreis")));
        gui.setItem(4, makeItem(Material.EMERALD, "§a§lKaufpreis",
            List.of("§6" + buyPrice + " §7Coins",
                    "",
                    "§7Preis wenn Spieler kaufen")));
        gui.setItem(5, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 1",   List.of("§7Kaufpreis")));
        gui.setItem(6, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 10",  List.of("§7Kaufpreis")));
        gui.setItem(7, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 100", List.of("§7Kaufpreis")));

        // ── Reihe 1: Verkaufen ───────────────────────────────────────────
        gui.setItem(9, sellOn
            ? makeItem(Material.LIME_CONCRETE, "§a§l✔ VERKAUFEN AKTIV",
                List.of("§7Spieler können Items verkaufen", "§7Klicken zum Deaktivieren"))
            : makeItem(Material.RED_CONCRETE,  "§c§l✘ VERKAUFEN INAKTIV",
                List.of("§7Spieler können nicht verkaufen", "§7Klicken zum Aktivieren")));
        gui.setItem(10, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 100", List.of("§7Verkaufspreis")));
        gui.setItem(11, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 10",  List.of("§7Verkaufspreis")));
        gui.setItem(12, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 1",   List.of("§7Verkaufspreis")));
        gui.setItem(13, makeItem(Material.REDSTONE, "§c§lVerkaufspreis",
            List.of("§6" + sellPrice + " §7Coins",
                    "",
                    "§7Preis den du zahlst wenn Spieler verkaufen")));
        gui.setItem(14, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 1",   List.of("§7Verkaufspreis")));
        gui.setItem(15, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 10",  List.of("§7Verkaufspreis")));
        gui.setItem(16, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 100", List.of("§7Verkaufspreis")));

        // ── Reihe 2: Menge + Item ────────────────────────────────────────
        gui.setItem(18, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 10", List.of("§7Menge")));
        gui.setItem(19, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 1",  List.of("§7Menge")));
        ItemStack itemDisplay = new ItemStack(item, Math.min(amount, 64));
        ItemMeta  im          = itemDisplay.getItemMeta();
        im.displayName(Component.text("§e§l" + formatName(item), TextColor.color(0xFFD700)));
        im.lore(List.of(
            Component.text("§7Menge pro Transaktion: §f" + amount),
            Component.text(""),
            Component.text("§8Slot 39: Item aus Truhe neu erkennen")));
        itemDisplay.setItemMeta(im);
        gui.setItem(22, itemDisplay);
        gui.setItem(25, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 1",  List.of("§7Menge")));
        gui.setItem(26, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 10", List.of("§7Menge")));

        // ── Reihe 3: Shop-Kasse ──────────────────────────────────────────
        gui.setItem(27, makeItem(Material.LIME_CONCRETE, "§a§l+ 10 Coins",
            List.of("§7Dein Konto: §f" + plugin.getEconomyManager().getBalance(player.getUniqueId()) + " Coins")));
        gui.setItem(28, makeItem(Material.LIME_CONCRETE,   "§a§l+ 100 Coins",   List.of("§7Einzahlen")));
        gui.setItem(29, makeItem(Material.LIME_CONCRETE,   "§a§l+ 1.000 Coins", List.of("§7Einzahlen")));
        gui.setItem(31, makeItem(Material.GOLD_INGOT, "§6§lShop-Kasse",
            List.of("§7Guthaben: §6§l" + balance + " §7Coins",
                    "",
                    "§7Zahlung beim Ankauf:",
                    useShop  ? "§a● Shop-Kasse"           : "§8○ Shop-Kasse",
                    !useShop ? "§a● Persönlich (Besitzer)" : "§8○ Persönlich (Besitzer)")));
        gui.setItem(33, makeItem(Material.RED_CONCRETE, "§c§l- 1.000 Coins", List.of("§7Auszahlen")));
        gui.setItem(34, makeItem(Material.RED_CONCRETE, "§c§l- 100 Coins",   List.of("§7Auszahlen")));
        gui.setItem(35, makeItem(Material.RED_CONCRETE, "§c§l- 10 Coins",    List.of("§7Auszahlen")));

        // ── Reihe 4: Aktionen ────────────────────────────────────────────
        gui.setItem(36, makeItem(Material.CHEST,      "§f§lTruhe öffnen",      List.of("§7Items einlegen oder entnehmen")));
        gui.setItem(39, makeItem(Material.HOPPER,     "§e§lItem neu erkennen", List.of("§7Liest erstes Item aus der Truhe")));
        gui.setItem(40, makeItem(Material.COMPARATOR, "§e§lZahlungsweise",
            List.of(useShop  ? "§a▶ Shop-Kasse (aktiv)"            : "§7  Shop-Kasse",
                    !useShop ? "§a▶ Persönlich / Besitzer (aktiv)" : "§7  Persönlich / Besitzer",
                    "", "§7Klicken zum Wechseln")));
        gui.setItem(44, makeItem(Material.BARRIER, "§c§lSchließen", List.of()));

        if (!alreadyOpen) player.openInventory(gui);
    }

    // ── GUI-Klick ────────────────────────────────────────────────────────

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            if (top.getHolder() instanceof ShopHolder || top.getHolder() instanceof SettingsHolder)
                event.setCancelled(true);
            return;
        }

        if (top.getHolder() instanceof ShopHolder holder) {
            event.setCancelled(true);
            handleShopClick(player, event.getSlot(), holder);
        } else if (top.getHolder() instanceof SettingsHolder holder) {
            event.setCancelled(true);
            handleSettingsClick(player, event.getSlot(), holder);
        }
    }

    private void handleShopClick(Player player, int slot, ShopHolder h) {
        if (h.block.getType() != Material.CHEST) {
            player.sendMessage("§cDer Shop existiert nicht mehr!");
            player.closeInventory();
            return;
        }
        if (slot == 2 && h.buyOn  && h.buyPrice  > 0) executeBuy (player, h.block, h);
        if (slot == 6 && h.sellOn && h.sellPrice > 0) executeSell(player, h.block, h);
    }

    private void handleSettingsClick(Player player, int slot, SettingsHolder h) {
        if (h.block.getType() != Material.CHEST) { player.closeInventory(); return; }
        switch (slot) {
            // ── Kaufen toggle + Preis ──
            case 0  -> toggleMode(player, h.block, buyOnKey);
            case 1  -> adjustPrice(player, h.block, buyKey, -100);
            case 2  -> adjustPrice(player, h.block, buyKey, -10);
            case 3  -> adjustPrice(player, h.block, buyKey, -1);
            case 5  -> adjustPrice(player, h.block, buyKey, +1);
            case 6  -> adjustPrice(player, h.block, buyKey, +10);
            case 7  -> adjustPrice(player, h.block, buyKey, +100);
            // ── Verkaufen toggle + Preis ──
            case 9  -> toggleMode(player, h.block, sellOnKey);
            case 10 -> adjustPrice(player, h.block, sellKey, -100);
            case 11 -> adjustPrice(player, h.block, sellKey, -10);
            case 12 -> adjustPrice(player, h.block, sellKey, -1);
            case 14 -> adjustPrice(player, h.block, sellKey, +1);
            case 15 -> adjustPrice(player, h.block, sellKey, +10);
            case 16 -> adjustPrice(player, h.block, sellKey, +100);
            // ── Menge ──
            case 18 -> adjustAmount(player, h.block, -10);
            case 19 -> adjustAmount(player, h.block, -1);
            case 25 -> adjustAmount(player, h.block, +1);
            case 26 -> adjustAmount(player, h.block, +10);
            // ── Kasse ──
            case 27 -> doDeposit (player, h.block, 10);
            case 28 -> doDeposit (player, h.block, 100);
            case 29 -> doDeposit (player, h.block, 1000);
            case 33 -> doWithdraw(player, h.block, 1000);
            case 34 -> doWithdraw(player, h.block, 100);
            case 35 -> doWithdraw(player, h.block, 10);
            // ── Aktionen ──
            case 36 -> { player.closeInventory();
                         Bukkit.getScheduler().runTask(plugin, () ->
                             player.openInventory(((Chest) h.block.getState()).getInventory())); }
            case 39 -> detectItemFromChest(player, h.block);
            case 40 -> doModeToggle(player, h.block);
            case 44 -> player.closeInventory();
        }
    }

    // ── Einstellungs-Aktionen ────────────────────────────────────────────

    private void toggleMode(Player player, Block block, NamespacedKey key) {
        Chest   chest   = (Chest) block.getState();
        boolean newVal  = getPdcInt(chest, key, 0) != 1;
        chest.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, newVal ? 1 : 0);
        chest.update();
        updateHologram(block);
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void adjustPrice(Player player, Block block, NamespacedKey key, long delta) {
        Chest chest    = (Chest) block.getState();
        long  current  = getPdc(chest, key, 1L);
        long  newPrice = Math.max(1, current + delta);
        chest.getPersistentDataContainer().set(key, PersistentDataType.LONG, newPrice);
        chest.update();
        updateHologram(block);
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void adjustAmount(Player player, Block block, int delta) {
        Chest chest  = (Chest) block.getState();
        int   newAmt = Math.max(1, getPdcInt(chest, amountKey, 1) + delta);
        chest.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, newAmt);
        chest.update();
        updateHologram(block);
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void detectItemFromChest(Player player, Block block) {
        Chest    chest = (Chest) block.getState();
        Material found = detectItem(chest);
        if (found == null) {
            player.sendMessage("§cTruhe ist leer!");
            return;
        }
        chest.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, found.name());
        chest.update();
        updateHologram(block);
        updateItemDisplay(block, found);
        player.sendMessage("§aItem: §f" + formatName(found));
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void doDeposit(Player player, Block block, long amount) {
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), amount)) {
            player.sendMessage("§cNicht genug Coins!");
            return;
        }
        Chest chest  = (Chest) block.getState();
        long  newBal = getPdc(chest, balanceKey, 0L) + amount;
        chest.getPersistentDataContainer().set(balanceKey, PersistentDataType.LONG, newBal);
        chest.update();
        player.sendMessage("§a+" + amount + " Coins §7(Kasse: §f" + newBal + "§7)");
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void doWithdraw(Player player, Block block, long amount) {
        Chest chest = (Chest) block.getState();
        long  bal   = getPdc(chest, balanceKey, 0L);
        if (bal < amount) {
            player.sendMessage("§cNur §f" + bal + "§c Coins in der Kasse!");
            return;
        }
        chest.getPersistentDataContainer().set(balanceKey, PersistentDataType.LONG, bal - amount);
        chest.update();
        plugin.getEconomyManager().deposit(player.getUniqueId(), amount);
        player.sendMessage("§a-" + amount + " Coins §7(Kasse: §f" + (bal - amount) + "§7)");
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void doModeToggle(Player player, Block block) {
        Chest   chest   = (Chest) block.getState();
        boolean newMode = getPdcInt(chest, useShopBalKey, 0) != 1;
        chest.getPersistentDataContainer().set(useShopBalKey, PersistentDataType.INTEGER, newMode ? 1 : 0);
        chest.update();
        player.sendMessage("§7Zahlungsweise: " + (newMode ? "§a§lShop-Kasse" : "§e§lPersönlich"));
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    // ── Kaufen ───────────────────────────────────────────────────────────

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
        removeItems(chest.getInventory(), h.item, h.amount);
        player.getInventory().addItem(new ItemStack(h.item, h.amount))
            .values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        plugin.getEconomyManager().deposit(h.ownerUUID, h.buyPrice);
        player.sendMessage("§aGekauft: §f" + h.amount + "x " + formatName(h.item)
            + " §afür §6" + h.buyPrice + " §aCoins");
        player.closeInventory();
    }

    // ── Verkaufen ────────────────────────────────────────────────────────

    private void executeSell(Player player, Block block, ShopHolder h) {
        Chest chest = (Chest) block.getState();
        if (countItems(player.getInventory(), h.item) < h.amount) {
            player.sendMessage("§cDu hast nicht genug §f" + formatName(h.item) + "§c!");
            return;
        }
        if (chest.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cDer Shop ist voll!");
            return;
        }
        boolean useShopBal = getPdcInt(chest, useShopBalKey, 0) == 1;
        if (useShopBal) {
            long shopBal = getPdc(chest, balanceKey, 0L);
            if (shopBal < h.sellPrice) {
                player.sendMessage("§cShop-Kasse hat nicht genug Coins!");
                return;
            }
            chest.getPersistentDataContainer().set(balanceKey, PersistentDataType.LONG, shopBal - h.sellPrice);
            chest.update();
        } else {
            if (!plugin.getEconomyManager().withdraw(h.ownerUUID, h.sellPrice)) {
                player.sendMessage("§cDer Shop-Besitzer hat nicht genug Coins!");
                return;
            }
        }
        removeItems(player.getInventory(), h.item, h.amount);
        chest.getInventory().addItem(new ItemStack(h.item, h.amount));
        plugin.getEconomyManager().deposit(player.getUniqueId(), h.sellPrice);
        player.sendMessage("§aVerkauft: §f" + h.amount + "x " + formatName(h.item)
            + " §afür §6" + h.sellPrice + " §aCoins");
        player.closeInventory();
    }

    // ── Hologramm ────────────────────────────────────────────────────────

    private void updateHologram(Block block) {
        Chest  chest   = (Chest) block.getState();
        String rawItem = chest.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (rawItem == null) return;
        Material mat = Material.matchMaterial(rawItem);
        if (mat == null) return;
        String   itemName  = formatName(mat);
        int      amount    = getPdcInt(chest, amountKey,  1);
        long     buyPrice  = getPdc(chest, buyKey,   0L);
        long     sellPrice = getPdc(chest, sellKey,  0L);
        boolean  buyOn     = getPdcInt(chest, buyOnKey,  0) == 1;
        boolean  sellOn    = getPdcInt(chest, sellOnKey, 0) == 1;
        Location holoLoc   = new Location(block.getWorld(),
            block.getX() + 0.5, block.getY() + 1.8, block.getZ() + 0.5);
        plugin.getHologramManager().create(shopHoloKey(block), holoLoc,
            shopHoloLines(itemName, amount, buyPrice, sellPrice, buyOn, sellOn), 0.85f);
    }

    public static String shopHoloKey(Block block) {
        return "shop_" + block.getWorld().getName()
            + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    public static List<String> shopHoloLines(String itemName, int amount, long buyPrice,
                                              long sellPrice, boolean buyOn, boolean sellOn) {
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>✦ Shop ✦</bold></gold>");
        lines.add("<white>" + amount + "x " + itemName + "</white>");
        if (buyOn  && buyPrice  > 0) lines.add("<green>Kaufen: <white>" + buyPrice  + " Coins</white></green>");
        if (sellOn && sellPrice > 0) lines.add("<red>Verkaufen: <white>" + sellPrice + " Coins</white></red>");
        return lines;
    }

    // ── PDC Hilfsmethoden ────────────────────────────────────────────────

    private long getPdc(Chest chest, NamespacedKey key, long def) {
        if (!chest.getPersistentDataContainer().has(key, PersistentDataType.LONG)) return def;
        Long val = chest.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        return val != null ? val : def;
    }

    private int getPdcInt(Chest chest, NamespacedKey key, int def) {
        if (!chest.getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) return def;
        Integer val = chest.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return val != null ? val : def;
    }

    private Material detectItem(Chest chest) {
        for (ItemStack s : chest.getInventory().getContents())
            if (s != null && s.getType() != Material.AIR) return s.getType();
        return null;
    }

    // ── Inventar-Hilfsmethoden ───────────────────────────────────────────

    private int countItems(org.bukkit.inventory.Inventory inv, Material mat) {
        int count = 0;
        for (ItemStack s : inv.getContents())
            if (s != null && s.getType() == mat) count += s.getAmount();
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

    // ── Item-Builder ─────────────────────────────────────────────────────

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

    public static String formatName(Material mat) {
        String s = mat.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Holders ──────────────────────────────────────────────────────────

    public static class ShopHolder implements InventoryHolder {
        final Block block; final Material item; final int amount;
        final long buyPrice, sellPrice; final boolean buyOn, sellOn; final UUID ownerUUID;

        ShopHolder(Block block, Material item, int amount, long buy, long sell,
                   boolean buyOn, boolean sellOn, UUID ownerUUID) {
            this.block = block; this.item = item; this.amount = amount;
            this.buyPrice = buy; this.sellPrice = sell;
            this.buyOn = buyOn; this.sellOn = sellOn; this.ownerUUID = ownerUUID;
        }
        @Override public Inventory getInventory() { return null; }
    }

    public static class SettingsHolder implements InventoryHolder {
        final Block block;
        SettingsHolder(Block block) { this.block = block; }
        @Override public Inventory getInventory() { return null; }
    }
}
