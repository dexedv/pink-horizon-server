package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Shop-GUI: Kaufen und Verkaufen von Items gegen Coins.
 * Tab-basiert: KAUFEN / VERKAUFEN
 */
public class ShopGui extends GuiBase {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ── Kaufpreise: Material → Preis pro Item ─────────────────────────────────
    public static final Map<Material, Long> BUY_PRICES = new LinkedHashMap<>();
    // ── Verkaufspreise: Material → Preis pro Item ─────────────────────────────
    public static final Map<Material, Long> SELL_PRICES = new LinkedHashMap<>();

    static {
        // Farming
        BUY_PRICES.put(Material.OAK_SAPLING,      5L);
        BUY_PRICES.put(Material.BIRCH_SAPLING,     5L);
        BUY_PRICES.put(Material.SPRUCE_SAPLING,    5L);
        BUY_PRICES.put(Material.JUNGLE_SAPLING,    8L);
        BUY_PRICES.put(Material.WHEAT_SEEDS,       3L);
        BUY_PRICES.put(Material.CARROT,            5L);
        BUY_PRICES.put(Material.POTATO,            5L);
        BUY_PRICES.put(Material.MELON_SEEDS,       8L);
        BUY_PRICES.put(Material.PUMPKIN_SEEDS,     8L);
        BUY_PRICES.put(Material.SUGAR_CANE,        4L);
        BUY_PRICES.put(Material.CACTUS,            10L);
        BUY_PRICES.put(Material.BONE_MEAL,         2L);
        // Materialien
        BUY_PRICES.put(Material.OBSIDIAN,          500L);
        BUY_PRICES.put(Material.END_STONE,         200L);
        BUY_PRICES.put(Material.NETHERRACK,        10L);
        BUY_PRICES.put(Material.SOUL_SAND,         50L);
        BUY_PRICES.put(Material.COBWEB,            300L);
        BUY_PRICES.put(Material.BLAZE_ROD,         150L);
        BUY_PRICES.put(Material.ENDER_PEARL,       200L);
        BUY_PRICES.put(Material.GHAST_TEAR,        500L);
        BUY_PRICES.put(Material.MAGMA_CREAM,       100L);
        // Werkzeuge
        BUY_PRICES.put(Material.WOODEN_PICKAXE,   50L);
        BUY_PRICES.put(Material.STONE_PICKAXE,    200L);
        BUY_PRICES.put(Material.IRON_PICKAXE,     1000L);
        BUY_PRICES.put(Material.BUCKET,            50L);
        BUY_PRICES.put(Material.WATER_BUCKET,      75L);
        BUY_PRICES.put(Material.LAVA_BUCKET,       150L);
        BUY_PRICES.put(Material.FLINT_AND_STEEL,   100L);
        BUY_PRICES.put(Material.FISHING_ROD,       80L);

        // Erze & Rohmaterialien verkaufen
        SELL_PRICES.put(Material.COBBLESTONE,      1L);
        SELL_PRICES.put(Material.STONE,            2L);
        SELL_PRICES.put(Material.GRAVEL,           1L);
        SELL_PRICES.put(Material.FLINT,            3L);
        SELL_PRICES.put(Material.COAL,             5L);
        SELL_PRICES.put(Material.COAL_ORE,         6L);
        SELL_PRICES.put(Material.DEEPSLATE_COAL_ORE, 7L);
        SELL_PRICES.put(Material.RAW_IRON,         10L);
        SELL_PRICES.put(Material.IRON_INGOT,       15L);
        SELL_PRICES.put(Material.IRON_ORE,         12L);
        SELL_PRICES.put(Material.DEEPSLATE_IRON_ORE, 13L);
        SELL_PRICES.put(Material.RAW_GOLD,         25L);
        SELL_PRICES.put(Material.GOLD_INGOT,       35L);
        SELL_PRICES.put(Material.GOLD_ORE,         28L);
        SELL_PRICES.put(Material.DEEPSLATE_GOLD_ORE, 30L);
        SELL_PRICES.put(Material.REDSTONE,         8L);
        SELL_PRICES.put(Material.LAPIS_LAZULI,     12L);
        SELL_PRICES.put(Material.DIAMOND,          500L);
        SELL_PRICES.put(Material.DIAMOND_ORE,      550L);
        SELL_PRICES.put(Material.DEEPSLATE_DIAMOND_ORE, 600L);
        SELL_PRICES.put(Material.EMERALD,          800L);
        SELL_PRICES.put(Material.EMERALD_ORE,      850L);
        SELL_PRICES.put(Material.ANCIENT_DEBRIS,   2500L);
        SELL_PRICES.put(Material.NETHERITE_SCRAP,  1800L);
        SELL_PRICES.put(Material.NETHERITE_INGOT,  8000L);
        // Holz
        SELL_PRICES.put(Material.OAK_LOG,          3L);
        SELL_PRICES.put(Material.BIRCH_LOG,        3L);
        SELL_PRICES.put(Material.SPRUCE_LOG,       3L);
        SELL_PRICES.put(Material.JUNGLE_LOG,       4L);
        SELL_PRICES.put(Material.ACACIA_LOG,       3L);
        SELL_PRICES.put(Material.DARK_OAK_LOG,     4L);
        // Farm-Produkte
        SELL_PRICES.put(Material.WHEAT,            2L);
        SELL_PRICES.put(Material.CARROT,           3L);
        SELL_PRICES.put(Material.POTATO,           3L);
        SELL_PRICES.put(Material.MELON_SLICE,      2L);
        SELL_PRICES.put(Material.PUMPKIN,          5L);
        SELL_PRICES.put(Material.SUGAR_CANE,       3L);
        SELL_PRICES.put(Material.CACTUS,           5L);
        SELL_PRICES.put(Material.STRING,           4L);
        SELL_PRICES.put(Material.LEATHER,          20L);
        SELL_PRICES.put(Material.FEATHER,          5L);
        SELL_PRICES.put(Material.EGG,              2L);
        // Fisch
        SELL_PRICES.put(Material.COD,              5L);
        SELL_PRICES.put(Material.SALMON,           8L);
        SELL_PRICES.put(Material.TROPICAL_FISH,    15L);
        SELL_PRICES.put(Material.PUFFERFISH,       20L);
    }

    private enum Tab { BUY, SELL }
    private Tab currentTab = Tab.BUY;

    private final PHSkyBlock plugin;
    private final Player player;

    // Buy-Items in Reihenfolge
    private static final Material[] BUY_ITEMS = BUY_PRICES.keySet().toArray(new Material[0]);
    // Sell-Items in Reihenfolge
    private static final Material[] SELL_ITEMS = SELL_PRICES.keySet().toArray(new Material[0]);

    private int page = 0;
    private static final int ITEMS_PER_PAGE = 28; // 4×7 Slots

    public ShopGui(PHSkyBlock plugin, Player player) {
        super("<gold><bold>⚑ Pink Horizon Shop", 6);
        this.plugin = plugin;
        this.player = player;
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.GRAY_STAINED_GLASS_PANE);

        // Tab-Buttons (Zeile 1)
        inventory.setItem(10, tab(Tab.BUY));
        inventory.setItem(16, tab(Tab.SELL));

        // Items anzeigen (Zeilen 2-5, Slots 19-46 ohne Rand)
        Material[] items = currentTab == Tab.BUY ? BUY_ITEMS : SELL_ITEMS;
        Map<Material, Long> prices = currentTab == Tab.BUY ? BUY_PRICES : SELL_PRICES;

        int[] slots = {
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
        };

        int start = page * slots.length;
        for (int i = 0; i < slots.length; i++) {
            int idx = start + i;
            if (idx >= items.length) break;
            Material mat = items[idx];
            long price = prices.get(mat);

            if (currentTab == Tab.BUY) {
                inventory.setItem(slots[i], item(mat,
                    "<yellow>" + prettyName(mat),
                    "<gray>Kaufpreis: <gold>" + String.format("%,d", price) + " Coins",
                    " ",
                    "<yellow>Linksklick: <white>1 kaufen",
                    "<yellow>Rechtsklick: <white>64 kaufen"
                ));
            } else {
                // Im Verkaufs-Tab: zeige wie viele der Spieler hat
                int playerCount = countInInventory(mat);
                long total = price * playerCount;
                inventory.setItem(slots[i], item(mat,
                    "<green>" + prettyName(mat),
                    "<gray>Verkaufspreis: <gold>" + String.format("%,d", price) + " Coins/Stk.",
                    "<gray>In deinem Inventar: <white>" + playerCount,
                    "<gray>Gesamtwert: <gold>" + String.format("%,d", total) + " Coins",
                    " ",
                    "<yellow>Linksklick: <white>1 Stapel verkaufen",
                    "<yellow>Rechtsklick: <white>Alle verkaufen"
                ));
            }
        }

        // Navigation (Zeile 6)
        if (page > 0) {
            inventory.setItem(45, item(Material.ARROW, "<gray>◄ Vorherige Seite"));
        }

        // Seiten-Anzeige
        int totalPages = (int) Math.ceil((double) items.length / slots.length);
        inventory.setItem(49, item(Material.PAPER,
            "<white>Seite " + (page + 1) + " / " + totalPages,
            "<gray>" + (currentTab == Tab.BUY ? "Kaufen" : "Verkaufen")
        ));

        if ((page + 1) * slots.length < items.length) {
            inventory.setItem(53, item(Material.ARROW, "<gray>Nächste Seite ►"));
        }
        inventory.setItem(48, closeButton());

        // Schnell-Alles-Verkaufen Button
        if (currentTab == Tab.SELL) {
            inventory.setItem(50, item(Material.EMERALD,
                "<green><bold>Alles verkaufen",
                "<gray>Verkauft alle Gegenstände",
                "<gray>aus deinem Inventar.",
                " ",
                "<yellow>Klicken zum Verkaufen"
            ));
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // Tab wechseln
        if (slot == 10) { currentTab = Tab.BUY;  page = 0; build(); return; }
        if (slot == 16) { currentTab = Tab.SELL; page = 0; build(); return; }

        // Navigation
        if (slot == 45) { page--; build(); return; }
        if (slot == 53) { page++; build(); return; }
        if (slot == 48) { player.closeInventory(); return; }

        // Alles verkaufen
        if (slot == 50 && currentTab == Tab.SELL) {
            sellAll();
            build();
            return;
        }

        // Item-Slots
        int[] slots = {
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
        };

        for (int i = 0; i < slots.length; i++) {
            if (slot != slots[i]) continue;
            int idx = page * slots.length + i;
            Material[] items = currentTab == Tab.BUY ? BUY_ITEMS : SELL_ITEMS;
            if (idx >= items.length) break;
            Material mat = items[idx];

            if (currentTab == Tab.BUY) {
                int amount = event.getClick() == ClickType.RIGHT ? 64 : 1;
                buyItem(mat, amount);
            } else {
                boolean all = event.getClick() == ClickType.RIGHT;
                sellItem(mat, all);
            }
            build();
            break;
        }
    }

    // ── Kauf-Logik ────────────────────────────────────────────────────────────

    private void buyItem(Material mat, int amount) {
        long price = BUY_PRICES.get(mat) * amount;
        if (!plugin.getCoinManager().deductCoins(player.getUniqueId(), price)) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>Shop</bold></light_purple><dark_gray>] "
                + "<red>Nicht genug Coins! Benötigt: <gold>" + String.format("%,d", price)));
            return;
        }
        var overflow = player.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount));
        if (!overflow.isEmpty()) {
            // Platz reicht nicht → Coins zurückgeben
            overflow.values().forEach(i -> {
                long refund = BUY_PRICES.get(mat) * i.getAmount();
                plugin.getCoinManager().addCoins(player.getUniqueId(), refund);
                player.getWorld().dropItemNaturally(player.getLocation(), i);
            });
        }
        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>Shop</bold></light_purple><dark_gray>] "
            + "<green>Gekauft: <white>" + amount + "× " + prettyName(mat)
            + " <gray>für <gold>" + String.format("%,d", price) + " Coins"));
    }

    // ── Verkauf-Logik ─────────────────────────────────────────────────────────

    private void sellItem(Material mat, boolean all) {
        long price = SELL_PRICES.get(mat);
        var inv = player.getInventory();
        long totalCoins = 0;
        int totalSold = 0;

        for (int i = 0; i < inv.getSize(); i++) {
            var item = inv.getItem(i);
            if (item == null || item.getType() != mat) continue;
            int toSell = all ? item.getAmount() : Math.min(item.getAmount(), 64);
            totalCoins += price * toSell;
            totalSold += toSell;
            item.setAmount(item.getAmount() - toSell);
            if (item.getAmount() <= 0) inv.setItem(i, null);
            if (!all) break;
        }

        if (totalSold == 0) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>Shop</bold></light_purple><dark_gray>] "
                + "<red>Du hast keine " + prettyName(mat) + " im Inventar."));
            return;
        }

        plugin.getCoinManager().addCoins(player.getUniqueId(), totalCoins);
        final long sold = totalSold;
        final long coins = totalCoins;
        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>Shop</bold></light_purple><dark_gray>] "
            + "<green>Verkauft: <white>" + sold + "× " + prettyName(mat)
            + " <gray>für <gold>" + String.format("%,d", coins) + " Coins"));

        // Quest-Tracking
        plugin.getQuestManager().onCoinsEarned(player.getUniqueId(), totalCoins);
    }

    private void sellAll() {
        var inv = player.getInventory();
        long totalCoins = 0;
        int types = 0;

        for (int i = 0; i < inv.getSize(); i++) {
            var item = inv.getItem(i);
            if (item == null) continue;
            Long price = SELL_PRICES.get(item.getType());
            if (price == null) continue;
            totalCoins += price * item.getAmount();
            types++;
            inv.setItem(i, null);
        }

        if (types == 0) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>Shop</bold></light_purple><dark_gray>] "
                + "<gray>Keine verkaufbaren Items im Inventar."));
            return;
        }

        plugin.getCoinManager().addCoins(player.getUniqueId(), totalCoins);
        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>Shop</bold></light_purple><dark_gray>] "
            + "<green>Alles verkauft! <gold>+" + String.format("%,d", totalCoins) + " Coins"));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        plugin.getQuestManager().onCoinsEarned(player.getUniqueId(), totalCoins);
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private int countInInventory(Material mat) {
        int count = 0;
        for (var item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) count += item.getAmount();
        }
        return count;
    }

    private org.bukkit.inventory.ItemStack tab(Tab t) {
        boolean active = t == currentTab;
        if (t == Tab.BUY) {
            return item(active ? Material.LIME_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE,
                active ? "<green><bold>▶ KAUFEN ◀" : "<gray>KAUFEN",
                active ? "<gray>Aktuell ausgewählt" : "<yellow>Klicken zum Wechseln"
            );
        } else {
            return item(active ? Material.LIME_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE,
                active ? "<green><bold>▶ VERKAUFEN ◀" : "<gray>VERKAUFEN",
                active ? "<gray>Aktuell ausgewählt" : "<yellow>Klicken zum Wechseln"
            );
        }
    }

    private String prettyName(Material mat) {
        String name = mat.name().toLowerCase().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
