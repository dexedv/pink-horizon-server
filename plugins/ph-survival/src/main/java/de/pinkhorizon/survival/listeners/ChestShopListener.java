package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.database.SurvivalDatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.*;
import java.util.*;

public class ChestShopListener implements Listener {

    // ── Shop-Daten ────────────────────────────────────────────────────────

    public static class ShopData {
        public UUID    ownerUuid;
        public Material item;
        public int     amount;
        public long    buyPrice, sellPrice;
        public boolean buyOn, sellOn;
        public long    balance;
        public boolean useShopBal;

        ShopData(UUID ownerUuid, Material item, int amount,
                 long buyPrice, long sellPrice, boolean buyOn, boolean sellOn,
                 long balance, boolean useShopBal) {
            this.ownerUuid  = ownerUuid;
            this.item       = item;
            this.amount     = amount;
            this.buyPrice   = buyPrice;
            this.sellPrice  = sellPrice;
            this.buyOn      = buyOn;
            this.sellOn     = sellOn;
            this.balance    = balance;
            this.useShopBal = useShopBal;
        }
    }

    private final PHSurvival plugin;
    private final SurvivalDatabaseManager db;

    /** Cache: "world_x_y_z" → ShopData */
    private final Map<String, ShopData> shopCache = new HashMap<>();

    private final Set<UUID> pendingCreate = new HashSet<>();
    private final Set<UUID> pendingRemove = new HashSet<>();
    private final Map<UUID, PendingChatInput> pendingChat = new HashMap<>();

    private static class PendingChatInput {
        final Block  block;
        final String field; // "buy", "sell"
        PendingChatInput(Block block, String field) { this.block = block; this.field = field; }
    }

    public ChestShopListener(PHSurvival plugin) {
        this.plugin = plugin;
        this.db     = plugin.getSurvivalDb();
    }

    // ── Startup ───────────────────────────────────────────────────────────

    public void loadAll() {
        shopCache.clear();
        String sql = "SELECT world, x, y, z, owner_uuid, item, amount, buy_price, sell_price, " +
                     "buy_on, sell_on, balance, use_shop_bal FROM sv_chestshops";
        List<Object[]> rows = new ArrayList<>();
        try (Connection con = db.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                rows.add(new Object[]{
                    rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                    rs.getString("owner_uuid"), rs.getString("item"), rs.getInt("amount"),
                    rs.getLong("buy_price"), rs.getLong("sell_price"),
                    rs.getBoolean("buy_on"), rs.getBoolean("sell_on"),
                    rs.getLong("balance"), rs.getBoolean("use_shop_bal")
                });
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[ChestShop] loadAll: " + e.getMessage());
            return;
        }

        int count = 0;
        for (Object[] row : rows) {
            String worldName = (String) row[0];
            int x = (int) row[1], y = (int) row[2], z = (int) row[3];
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            Block chestBlock = world.getBlockAt(x, y, z);
            if (chestBlock.getType() != Material.CHEST) {
                deleteShopFromDb(chestBlock);
                continue;
            }
            Material item = Material.matchMaterial((String) row[5]);
            if (item == null) item = Material.STONE;
            ShopData data = new ShopData(
                UUID.fromString((String) row[4]), item, (int) row[6],
                (long) row[7], (long) row[8], (boolean) row[9], (boolean) row[10],
                (long) row[11], (boolean) row[12]);
            shopCache.put(locKey(chestBlock), data);
            Block glassBlock = chestBlock.getRelative(0, 1, 0);
            if (glassBlock.getType() == Material.WHITE_STAINED_GLASS)
                spawnItemDisplay(glassBlock, data.item);
            updateHologram(chestBlock);
            count++;
        }
        plugin.getLogger().info(count + " ChestShop(s) geladen.");
    }

    public void addPendingCreate(UUID uuid)   { pendingCreate.add(uuid); }
    public void startPendingRemove(Player p)  { pendingRemove.add(p.getUniqueId()); p.sendMessage("§cKlicke auf deinen Shop um ihn zu entfernen."); }

    // ── Interact ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        if (pendingCreate.contains(player.getUniqueId())) {
            if (block.getType() != Material.CHEST) { player.sendMessage("§cDas ist keine Truhe!"); return; }
            pendingCreate.remove(player.getUniqueId());
            event.setCancelled(true);
            createShop(player, block);
            return;
        }

        if (block.getType() != Material.WHITE_STAINED_GLASS) return;
        Block chestBlock = block.getRelative(0, -1, 0);
        if (chestBlock.getType() != Material.CHEST) return;
        ShopData data = shopCache.get(locKey(chestBlock));
        if (data == null) return;

        event.setCancelled(true);

        if (pendingRemove.remove(player.getUniqueId())) {
            if (!player.getUniqueId().equals(data.ownerUuid) && !player.isOp()) {
                player.sendMessage("§cDas ist nicht dein Shop!");
                return;
            }
            removeShop(player, block, chestBlock, data);
            return;
        }

        if (player.getUniqueId().equals(data.ownerUuid)) {
            openSettingsGUI(player, chestBlock);
            return;
        }

        String ownerName = Bukkit.getOfflinePlayer(data.ownerUuid).getName();
        openShopGUI(player, chestBlock, data, ownerName);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakGlass(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.WHITE_STAINED_GLASS) return;
        Block chestBlock = block.getRelative(0, -1, 0);
        if (chestBlock.getType() != Material.CHEST) return;
        ShopData data = shopCache.get(locKey(chestBlock));
        if (data == null) return;
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(data.ownerUuid) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage("§cDas ist ein fremder Shop!");
            return;
        }
        event.setCancelled(true);
        removeShop(player, block, chestBlock, data);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakChest(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;
        if (!shopCache.containsKey(locKey(block))) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cEntferne zuerst das Shop-Glas darüber!");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingCreate.remove(id);
        pendingRemove.remove(id);
        pendingChat.remove(id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent event) {
        PendingChatInput pending = pendingChat.remove(event.getPlayer().getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String msg = event.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("abbrechen")) {
            Bukkit.getScheduler().runTask(plugin, () -> { player.sendMessage("§7Eingabe abgebrochen."); openSettingsGUI(player, pending.block); });
            return;
        }
        try {
            long value = Long.parseLong(msg);
            if (value < 1) throw new NumberFormatException();
            Bukkit.getScheduler().runTask(plugin, () -> {
                ShopData data = shopCache.get(locKey(pending.block));
                if (data == null) { player.sendMessage("§cShop nicht mehr gefunden!"); return; }
                if ("buy".equals(pending.field)) data.buyPrice = value;
                else                             data.sellPrice = value;
                saveShopToDb(pending.block, data);
                updateHologram(pending.block);
                player.sendMessage("§aPreis gesetzt: §6" + value + " §7Coins");
                openSettingsGUI(player, pending.block);
            });
        } catch (NumberFormatException e) {
            player.sendMessage("§cUngültige Zahl! Nur positive ganze Zahlen erlaubt.");
            Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, pending.block));
        }
    }

    // ── Shop erstellen / entfernen ───────────────────────────────────────

    private void createShop(Player player, Block block) {
        if (shopCache.containsKey(locKey(block))) { player.sendMessage("§cDiese Truhe ist bereits ein Shop!"); return; }
        if (!player.isOp()) {
            org.bukkit.Chunk chunk = block.getChunk();
            if (plugin.getClaimManager().isClaimed(chunk)
                    && !plugin.getClaimManager().isTrusted(chunk, player.getUniqueId())) {
                player.sendMessage("§cDu kannst hier keinen Shop erstellen (fremder Claim)!");
                return;
            }
        }
        Block glassBlock = block.getRelative(0, 1, 0);
        if (!glassBlock.getType().isAir()) { player.sendMessage("§cÜber der Truhe muss ein freier Block sein!"); return; }

        Material shopItem = detectItem((Chest) block.getState());
        if (shopItem == null) shopItem = Material.STONE;

        ShopData data = new ShopData(player.getUniqueId(), shopItem, 1, 10, 5, false, false, 0, false);
        shopCache.put(locKey(block), data);
        saveShopToDb(block, data);

        glassBlock.setType(Material.WHITE_STAINED_GLASS);
        spawnItemDisplay(glassBlock, shopItem);
        updateHologram(block);
        player.sendMessage("§aShop erstellt! §7Aktiviere Kaufen/Verkaufen im GUI.");
        openSettingsGUI(player, block);
    }

    private void removeShop(Player player, Block glassBlock, Block chestBlock, ShopData data) {
        if (data.balance > 0) {
            plugin.getEconomyManager().deposit(data.ownerUuid, data.balance);
            player.sendMessage("§7Shop-Kasse ausgezahlt: §6" + data.balance + " §7Coins");
        }
        removeItemDisplay(glassBlock);
        glassBlock.setType(Material.AIR);
        shopCache.remove(locKey(chestBlock));
        deleteShopFromDb(chestBlock);
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
                new org.joml.Vector3f(0, 0, 0), new org.joml.AxisAngle4f(0, 0, 0, 1),
                new org.joml.Vector3f(0.6f, 0.6f, 0.6f), new org.joml.AxisAngle4f(0, 0, 0, 1)));
            display.setCustomName("shop_display");
            display.setCustomNameVisible(false);
        });
    }

    private void removeItemDisplay(Block glassBlock) {
        Location center = glassBlock.getLocation().add(0.5, 0.5, 0.5);
        glassBlock.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8).stream()
            .filter(e -> e instanceof org.bukkit.entity.ItemDisplay && "shop_display".equals(e.getCustomName()))
            .forEach(org.bukkit.entity.Entity::remove);
    }

    private void updateItemDisplay(Block chestBlock, Material item) {
        Block glassBlock = chestBlock.getRelative(0, 1, 0);
        if (glassBlock.getType() == Material.WHITE_STAINED_GLASS) spawnItemDisplay(glassBlock, item);
    }

    // ── Kunden-GUI ───────────────────────────────────────────────────────

    private void openShopGUI(Player player, Block block, ShopData data, String ownerName) {
        ShopHolder holder = new ShopHolder(block, data.item, data.amount,
            data.buyPrice, data.sellPrice, data.buyOn, data.sellOn, data.ownerUuid);
        Inventory gui = Bukkit.createInventory(holder, 9, Component.text("§6[Shop] §f" + ownerName));
        if (data.buyOn && data.buyPrice > 0)
            gui.setItem(2, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a§lKAUFEN",
                List.of("§7" + data.amount + "x §f" + formatName(data.item), "§7Preis: §6" + data.buyPrice + " §7Coins")));
        ItemStack display = new ItemStack(data.item, Math.min(data.amount, 64));
        ItemMeta dm = display.getItemMeta();
        dm.displayName(Component.text(formatName(data.item), TextColor.color(0xFFD700)));
        dm.lore(List.of(
            Component.text("§7Menge: §f" + data.amount),
            data.buyOn && data.buyPrice > 0 ? Component.text("§7Kaufen: §f" + data.buyPrice + " Coins") : Component.text("§8Kaufen: deaktiviert"),
            data.sellOn && data.sellPrice > 0 ? Component.text("§7Verkaufen: §f" + data.sellPrice + " Coins") : Component.text("§8Verkaufen: deaktiviert")
        ));
        display.setItemMeta(dm);
        gui.setItem(4, display);
        if (data.sellOn && data.sellPrice > 0)
            gui.setItem(6, makeItem(Material.RED_STAINED_GLASS_PANE, "§c§lVERKAUFEN",
                List.of("§7" + data.amount + "x §f" + formatName(data.item), "§7Erlös: §6" + data.sellPrice + " §7Coins")));
        player.openInventory(gui);
    }

    // ── Settings-GUI ─────────────────────────────────────────────────────

    private void openSettingsGUI(Player player, Block block) {
        ShopData data = shopCache.get(locKey(block));
        if (data == null) { player.sendMessage("§cShop nicht gefunden!"); return; }

        boolean alreadyOpen = player.getOpenInventory().getTopInventory().getHolder() instanceof SettingsHolder;
        Inventory gui;
        if (alreadyOpen) {
            gui = player.getOpenInventory().getTopInventory();
        } else {
            gui = Bukkit.createInventory(new SettingsHolder(block), 45, Component.text("§6⚙ Shop Einstellungen"));
        }

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of());
        for (int i = 0; i < 45; i++) gui.setItem(i, filler);

        gui.setItem(0, data.buyOn
            ? makeItem(Material.LIME_CONCRETE, "§a§l✔ KAUFEN AKTIV", List.of("§7Spieler können Items kaufen", "§7Klicken zum Deaktivieren"))
            : makeItem(Material.RED_CONCRETE,  "§c§l✘ KAUFEN INAKTIV", List.of("§7Spieler können keine Items kaufen", "§7Klicken zum Aktivieren")));
        gui.setItem(1, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 100", List.of("§7Kaufpreis")));
        gui.setItem(2, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 10",  List.of("§7Kaufpreis")));
        gui.setItem(3, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 1",   List.of("§7Kaufpreis")));
        gui.setItem(4, makeItem(Material.EMERALD, "§a§lKaufpreis",
            List.of("§6" + data.buyPrice + " §7Coins", "", "§eKlicken → Zahl in Chat eingeben")));
        gui.setItem(5, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 1",   List.of("§7Kaufpreis")));
        gui.setItem(6, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 10",  List.of("§7Kaufpreis")));
        gui.setItem(7, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 100", List.of("§7Kaufpreis")));

        gui.setItem(9, data.sellOn
            ? makeItem(Material.LIME_CONCRETE, "§a§l✔ VERKAUFEN AKTIV", List.of("§7Spieler können Items verkaufen", "§7Klicken zum Deaktivieren"))
            : makeItem(Material.RED_CONCRETE,  "§c§l✘ VERKAUFEN INAKTIV", List.of("§7Spieler können nicht verkaufen", "§7Klicken zum Aktivieren")));
        gui.setItem(10, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 100", List.of("§7Verkaufspreis")));
        gui.setItem(11, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 10",  List.of("§7Verkaufspreis")));
        gui.setItem(12, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 1",   List.of("§7Verkaufspreis")));
        gui.setItem(13, makeItem(Material.REDSTONE, "§c§lVerkaufspreis",
            List.of("§6" + data.sellPrice + " §7Coins", "", "§eKlicken → Zahl in Chat eingeben")));
        gui.setItem(14, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 1",   List.of("§7Verkaufspreis")));
        gui.setItem(15, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 10",  List.of("§7Verkaufspreis")));
        gui.setItem(16, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 100", List.of("§7Verkaufspreis")));

        gui.setItem(18, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 10", List.of("§7Menge")));
        gui.setItem(19, makeItem(Material.RED_STAINED_GLASS_PANE,  "§c- 1",  List.of("§7Menge")));
        ItemStack itemDisplay = new ItemStack(data.item, Math.min(data.amount, 64));
        ItemMeta im = itemDisplay.getItemMeta();
        im.displayName(Component.text("§e§l" + formatName(data.item), TextColor.color(0xFFD700)));
        im.lore(List.of(Component.text("§7Menge pro Transaktion: §f" + data.amount), Component.text(""), Component.text("§8Slot 39: Item aus Truhe neu erkennen")));
        itemDisplay.setItemMeta(im);
        gui.setItem(22, itemDisplay);
        gui.setItem(25, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 1",  List.of("§7Menge")));
        gui.setItem(26, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a+ 10", List.of("§7Menge")));

        gui.setItem(27, makeItem(Material.LIME_CONCRETE, "§a§l+ 10 Coins",
            List.of("§7Dein Konto: §f" + plugin.getEconomyManager().getBalance(player.getUniqueId()) + " Coins")));
        gui.setItem(28, makeItem(Material.LIME_CONCRETE, "§a§l+ 100 Coins",   List.of("§7Einzahlen")));
        gui.setItem(29, makeItem(Material.LIME_CONCRETE, "§a§l+ 1.000 Coins", List.of("§7Einzahlen")));
        gui.setItem(31, makeItem(Material.GOLD_INGOT, "§6§lShop-Kasse",
            List.of("§7Guthaben: §6§l" + data.balance + " §7Coins", "",
                    "§7Zahlung beim Ankauf:",
                    data.useShopBal ? "§a● Shop-Kasse" : "§8○ Shop-Kasse",
                    !data.useShopBal ? "§a● Persönlich (Besitzer)" : "§8○ Persönlich (Besitzer)")));
        gui.setItem(33, makeItem(Material.RED_CONCRETE, "§c§l- 1.000 Coins", List.of("§7Auszahlen")));
        gui.setItem(34, makeItem(Material.RED_CONCRETE, "§c§l- 100 Coins",   List.of("§7Auszahlen")));
        gui.setItem(35, makeItem(Material.RED_CONCRETE, "§c§l- 10 Coins",    List.of("§7Auszahlen")));

        gui.setItem(36, makeItem(Material.CHEST,      "§f§lTruhe öffnen",      List.of("§7Items einlegen oder entnehmen")));
        gui.setItem(39, makeItem(Material.HOPPER,     "§e§lItem neu erkennen", List.of("§7Liest erstes Item aus der Truhe")));
        gui.setItem(40, makeItem(Material.COMPARATOR, "§e§lZahlungsweise",
            List.of(data.useShopBal ? "§a▶ Shop-Kasse (aktiv)" : "§7  Shop-Kasse",
                    !data.useShopBal ? "§a▶ Persönlich / Besitzer (aktiv)" : "§7  Persönlich / Besitzer",
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
        if (h.block.getType() != Material.CHEST) { player.sendMessage("§cDer Shop existiert nicht mehr!"); player.closeInventory(); return; }
        if (slot == 2 && h.buyOn  && h.buyPrice  > 0) executeBuy (player, h.block, h);
        if (slot == 6 && h.sellOn && h.sellPrice > 0) executeSell(player, h.block, h);
    }

    private void handleSettingsClick(Player player, int slot, SettingsHolder h) {
        if (h.block.getType() != Material.CHEST) { player.closeInventory(); return; }
        switch (slot) {
            case 0  -> toggleField(player, h.block, "buyOn");
            case 1  -> adjustPrice(player, h.block, "buy",  -100);
            case 2  -> adjustPrice(player, h.block, "buy",  -10);
            case 3  -> adjustPrice(player, h.block, "buy",  -1);
            case 4  -> startChatInput(player, h.block, "buy",  "Kaufpreis");
            case 5  -> adjustPrice(player, h.block, "buy",  +1);
            case 6  -> adjustPrice(player, h.block, "buy",  +10);
            case 7  -> adjustPrice(player, h.block, "buy",  +100);
            case 9  -> toggleField(player, h.block, "sellOn");
            case 10 -> adjustPrice(player, h.block, "sell", -100);
            case 11 -> adjustPrice(player, h.block, "sell", -10);
            case 12 -> adjustPrice(player, h.block, "sell", -1);
            case 13 -> startChatInput(player, h.block, "sell", "Verkaufspreis");
            case 14 -> adjustPrice(player, h.block, "sell", +1);
            case 15 -> adjustPrice(player, h.block, "sell", +10);
            case 16 -> adjustPrice(player, h.block, "sell", +100);
            case 18 -> adjustAmount(player, h.block, -10);
            case 19 -> adjustAmount(player, h.block, -1);
            case 25 -> adjustAmount(player, h.block, +1);
            case 26 -> adjustAmount(player, h.block, +10);
            case 27 -> doDeposit (player, h.block, 10);
            case 28 -> doDeposit (player, h.block, 100);
            case 29 -> doDeposit (player, h.block, 1000);
            case 33 -> doWithdraw(player, h.block, 1000);
            case 34 -> doWithdraw(player, h.block, 100);
            case 35 -> doWithdraw(player, h.block, 10);
            case 36 -> { player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.openInventory(((Chest) h.block.getState()).getInventory())); }
            case 39 -> detectItemFromChest(player, h.block);
            case 40 -> toggleField(player, h.block, "useShopBal");
            case 44 -> player.closeInventory();
        }
    }

    // ── Einstellungs-Aktionen ────────────────────────────────────────────

    private void startChatInput(Player player, Block block, String field, String label) {
        pendingChat.put(player.getUniqueId(), new PendingChatInput(block, field));
        player.closeInventory();
        player.sendMessage("§6§l[Shop] §e" + label + " eingeben:");
        player.sendMessage("§7Schreib eine Zahl in den Chat. §8(Abbrechen: §7cancel§8)");
    }

    private void toggleField(Player player, Block block, String field) {
        ShopData data = shopCache.get(locKey(block));
        if (data == null) return;
        switch (field) {
            case "buyOn"      -> data.buyOn      = !data.buyOn;
            case "sellOn"     -> data.sellOn     = !data.sellOn;
            case "useShopBal" -> { data.useShopBal = !data.useShopBal;
                player.sendMessage("§7Zahlungsweise: " + (data.useShopBal ? "§a§lShop-Kasse" : "§e§lPersönlich")); }
        }
        saveShopToDb(block, data);
        updateHologram(block);
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void adjustPrice(Player player, Block block, String field, long delta) {
        ShopData data = shopCache.get(locKey(block));
        if (data == null) return;
        if ("buy".equals(field))  data.buyPrice  = Math.max(1, data.buyPrice  + delta);
        else                      data.sellPrice = Math.max(1, data.sellPrice + delta);
        saveShopToDb(block, data);
        updateHologram(block);
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void adjustAmount(Player player, Block block, int delta) {
        ShopData data = shopCache.get(locKey(block));
        if (data == null) return;
        data.amount = Math.max(1, data.amount + delta);
        saveShopToDb(block, data);
        updateHologram(block);
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void detectItemFromChest(Player player, Block block) {
        ShopData data = shopCache.get(locKey(block));
        if (data == null) return;
        Material found = detectItem((Chest) block.getState());
        if (found == null) { player.sendMessage("§cTruhe ist leer!"); return; }
        data.item = found;
        saveShopToDb(block, data);
        updateHologram(block);
        updateItemDisplay(block, found);
        player.sendMessage("§aItem: §f" + formatName(found));
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void doDeposit(Player player, Block block, long amount) {
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), amount)) { player.sendMessage("§cNicht genug Coins!"); return; }
        ShopData data = shopCache.get(locKey(block));
        if (data == null) return;
        data.balance += amount;
        saveShopToDb(block, data);
        player.sendMessage("§a+" + amount + " Coins §7(Kasse: §f" + data.balance + "§7)");
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    private void doWithdraw(Player player, Block block, long amount) {
        ShopData data = shopCache.get(locKey(block));
        if (data == null) return;
        if (data.balance < amount) { player.sendMessage("§cNur §f" + data.balance + "§c Coins in der Kasse!"); return; }
        data.balance -= amount;
        saveShopToDb(block, data);
        plugin.getEconomyManager().deposit(player.getUniqueId(), amount);
        player.sendMessage("§a-" + amount + " Coins §7(Kasse: §f" + data.balance + "§7)");
        Bukkit.getScheduler().runTask(plugin, () -> openSettingsGUI(player, block));
    }

    // ── Kaufen / Verkaufen ───────────────────────────────────────────────

    private void executeBuy(Player player, Block block, ShopHolder h) {
        Chest chest = (Chest) block.getState();
        if (countItems(chest.getInventory(), h.item) < h.amount) { player.sendMessage("§cNicht auf Lager!"); player.closeInventory(); return; }
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), h.buyPrice)) { player.sendMessage("§cNicht genug Coins! (§f" + h.buyPrice + "§c benötigt)"); return; }
        removeItems(chest.getInventory(), h.item, h.amount);
        player.getInventory().addItem(new ItemStack(h.item, h.amount)).values()
            .forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        plugin.getEconomyManager().deposit(h.ownerUUID, h.buyPrice);
        player.sendMessage("§aGekauft: §f" + h.amount + "x " + formatName(h.item) + " §afür §6" + h.buyPrice + " §aCoins");
        player.closeInventory();
    }

    private void executeSell(Player player, Block block, ShopHolder h) {
        Chest chest = (Chest) block.getState();
        if (countItems(player.getInventory(), h.item) < h.amount) { player.sendMessage("§cDu hast nicht genug §f" + formatName(h.item) + "§c!"); return; }
        if (chest.getInventory().firstEmpty() == -1) { player.sendMessage("§cDer Shop ist voll!"); return; }
        ShopData data = shopCache.get(locKey(block));
        if (data == null) return;
        if (data.useShopBal) {
            if (data.balance < h.sellPrice) { player.sendMessage("§cShop-Kasse hat nicht genug Coins!"); return; }
            data.balance -= h.sellPrice;
            saveShopToDb(block, data);
        } else {
            if (!plugin.getEconomyManager().withdraw(h.ownerUUID, h.sellPrice)) { player.sendMessage("§cDer Shop-Besitzer hat nicht genug Coins!"); return; }
        }
        removeItems(player.getInventory(), h.item, h.amount);
        chest.getInventory().addItem(new ItemStack(h.item, h.amount));
        plugin.getEconomyManager().deposit(player.getUniqueId(), h.sellPrice);
        player.sendMessage("§aVerkauft: §f" + h.amount + "x " + formatName(h.item) + " §afür §6" + h.sellPrice + " §aCoins");
        player.closeInventory();
    }

    // ── Hologramm ────────────────────────────────────────────────────────

    private void updateHologram(Block block) {
        ShopData data = shopCache.get(locKey(block));
        if (data == null) return;
        Location holoLoc = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 1.8, block.getZ() + 0.5);
        plugin.getHologramManager().create(shopHoloKey(block), holoLoc,
            shopHoloLines(formatName(data.item), data.amount, data.buyPrice, data.sellPrice, data.buyOn, data.sellOn), 0.85f);
    }

    public static String shopHoloKey(Block block) {
        return "shop_" + block.getWorld().getName() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    public static List<String> shopHoloLines(String itemName, int amount, long buyPrice,
                                              long sellPrice, boolean buyOn, boolean sellOn) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("<gold><bold>✦ Shop ✦</bold></gold>");
        lines.add("<white>" + amount + "x " + itemName + "</white>");
        if (buyOn  && buyPrice  > 0) lines.add("<green>Kaufen: <white>" + buyPrice  + " Coins</white></green>");
        if (sellOn && sellPrice > 0) lines.add("<red>Verkaufen: <white>" + sellPrice + " Coins</white></red>");
        return lines;
    }

    // ── DB-Hilfsmethoden ─────────────────────────────────────────────────

    private void saveShopToDb(Block chestBlock, ShopData data) {
        String sql = "INSERT INTO sv_chestshops (world, x, y, z, owner_uuid, item, amount, " +
                     "buy_price, sell_price, buy_on, sell_on, balance, use_shop_bal) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE owner_uuid=VALUES(owner_uuid), item=VALUES(item), " +
                     "amount=VALUES(amount), buy_price=VALUES(buy_price), sell_price=VALUES(sell_price), " +
                     "buy_on=VALUES(buy_on), sell_on=VALUES(sell_on), balance=VALUES(balance), use_shop_bal=VALUES(use_shop_bal)";
        try (Connection con = db.getConnection(); PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, chestBlock.getWorld().getName());
            stmt.setInt(2, chestBlock.getX());
            stmt.setInt(3, chestBlock.getY());
            stmt.setInt(4, chestBlock.getZ());
            stmt.setString(5, data.ownerUuid.toString());
            stmt.setString(6, data.item.name());
            stmt.setInt(7, data.amount);
            stmt.setLong(8, data.buyPrice);
            stmt.setLong(9, data.sellPrice);
            stmt.setBoolean(10, data.buyOn);
            stmt.setBoolean(11, data.sellOn);
            stmt.setLong(12, data.balance);
            stmt.setBoolean(13, data.useShopBal);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[ChestShop] saveShopToDb: " + e.getMessage());
        }
    }

    private void deleteShopFromDb(Block chestBlock) {
        String sql = "DELETE FROM sv_chestshops WHERE world=? AND x=? AND y=? AND z=?";
        try (Connection con = db.getConnection(); PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, chestBlock.getWorld().getName());
            stmt.setInt(2, chestBlock.getX());
            stmt.setInt(3, chestBlock.getY());
            stmt.setInt(4, chestBlock.getZ());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[ChestShop] deleteShopFromDb: " + e.getMessage());
        }
    }

    // ── Inventar-Hilfsmethoden ───────────────────────────────────────────

    private static String locKey(Block block) {
        return block.getWorld().getName() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

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
            if (contents[i].getAmount() <= left) { left -= contents[i].getAmount(); contents[i] = null; }
            else { contents[i].setAmount(contents[i].getAmount() - left); left = 0; }
        }
        inv.setContents(contents);
    }

    private Material detectItem(Chest chest) {
        for (ItemStack s : chest.getInventory().getContents())
            if (s != null && s.getType() != Material.AIR) return s.getType();
        return null;
    }

    private ItemStack makeItem(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new java.util.ArrayList<>();
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
        ShopHolder(Block b, Material i, int a, long buy, long sell, boolean buyOn, boolean sellOn, UUID o) {
            this.block = b; this.item = i; this.amount = a;
            this.buyPrice = buy; this.sellPrice = sell;
            this.buyOn = buyOn; this.sellOn = sellOn; this.ownerUUID = o;
        }
        @Override public Inventory getInventory() { return null; }
    }

    public static class SettingsHolder implements InventoryHolder {
        final Block block;
        SettingsHolder(Block block) { this.block = block; }
        @Override public Inventory getInventory() { return null; }
    }
}
