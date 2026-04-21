package de.pinkhorizon.minigames.gui;

import de.pinkhorizon.minigames.PHMinigames;
import de.pinkhorizon.minigames.bedwars.BedWarsGame;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * BedWars-Shop (54 Slots, wird per Rechtsklick auf Villager-NPC geöffnet).
 * Spieler öffnen den Shop per /bw shop oder durch Rechtsklick auf einen designierten Villager.
 */
public class BedWarsShopGui {

    private static final String TITLE = "§d§lBedWars-Shop";

    private enum ShopItem {
        // Blöcke (Iron)
        WOOL       (Material.WHITE_WOOL,     16, Material.IRON_INGOT,  4,  "§fWolle (16x)",           "§7Preis: §74 Eisen",  0),
        PLANKS     (Material.OAK_PLANKS,     16, Material.IRON_INGOT,  4,  "§fBretter (16x)",          "§7Preis: §74 Eisen",  1),
        SANDSTONE  (Material.SANDSTONE,       8, Material.IRON_INGOT,  8,  "§fSandstein (8x)",         "§7Preis: §78 Eisen",  2),
        END_STONE  (Material.END_STONE,       4, Material.IRON_INGOT, 12,  "§fEndstein (4x)",          "§7Preis: §712 Eisen", 3),

        // Werkzeug (Iron/Gold)
        IRON_SWORD  (Material.IRON_SWORD,    1, Material.GOLD_INGOT,  10, "§fEisenschwert",           "§7Preis: §610 Gold",  9),
        WOOD_AXE    (Material.WOODEN_AXE,    1, Material.IRON_INGOT,   6, "§fHolzaxt",               "§7Preis: §76 Eisen",  10),
        IRON_PICK   (Material.IRON_PICKAXE,  1, Material.GOLD_INGOT,  10, "§fEisenspitzhacke",       "§7Preis: §610 Gold",  11),
        SHEARS      (Material.SHEARS,        1, Material.IRON_INGOT,  20, "§fSchere",                "§7Preis: §720 Eisen", 12),

        // Rüstung (Gold/Diamond)
        IRON_ARMOR  (Material.IRON_CHESTPLATE, 1, Material.GOLD_INGOT, 40, "§fEisen-Brustplatte",    "§7Preis: §640 Gold",  18),
        CHAIN_ARMOR (Material.CHAINMAIL_CHESTPLATE,1, Material.GOLD_INGOT,24,"§fKettenbrustplatte",  "§7Preis: §624 Gold",  19),

        // Bögen/Pfeile (Gold)
        BOW         (Material.BOW,           1, Material.GOLD_INGOT,  12, "§fBogen",                 "§7Preis: §612 Gold",  27),
        ARROWS      (Material.ARROW,         8, Material.GOLD_INGOT,   2, "§fPfeile (8x)",           "§7Preis: §62 Gold",   28),

        // Spezial (Diamond/Emerald)
        TNT         (Material.TNT,           1, Material.DIAMOND,      4, "§cTNT",                   "§7Preis: §b4 Diamanten", 36),
        ENDER_PEARL (Material.ENDER_PEARL,   1, Material.DIAMOND,      4, "§5Enderperle",            "§7Preis: §b4 Diamanten", 37),
        FIRE_BALL   (Material.FIRE_CHARGE,   1, Material.GOLD_INGOT,   6, "§6Feuerball",             "§7Preis: §66 Gold",   38);

        final Material item;
        final int      amount;
        final Material currency;
        final int      price;
        final String   displayName;
        final String   lore;
        final int      slot;

        ShopItem(Material item, int amount, Material currency, int price,
                 String displayName, String lore, int slot) {
            this.item = item; this.amount = amount; this.currency = currency;
            this.price = price; this.displayName = displayName; this.lore = lore; this.slot = slot;
        }
    }

    private final PHMinigames plugin;

    public BedWarsShopGui(PHMinigames plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        BedWarsGame game = plugin.getArenaManager().getGameOf(player.getUniqueId());
        if (game == null || game.getState() != BedWarsGame.GameState.RUNNING) {
            player.sendMessage("§cDu bist nicht in einem aktiven Spiel.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Kategorie-Trenner
        addSeparator(inv, 0, "§6Blöcke",  Material.ORANGE_STAINED_GLASS_PANE);
        addSeparator(inv, 9, "§fWerkzeug", Material.GRAY_STAINED_GLASS_PANE);
        addSeparator(inv, 18, "§bRüstung", Material.CYAN_STAINED_GLASS_PANE);
        addSeparator(inv, 27, "§aBogen",   Material.LIME_STAINED_GLASS_PANE);
        addSeparator(inv, 36, "§cSpezial", Material.RED_STAINED_GLASS_PANE);

        for (ShopItem si : ShopItem.values()) {
            ItemStack is = new ItemStack(si.item, si.amount);
            ItemMeta meta = is.getItemMeta();
            meta.setDisplayName(si.displayName);
            meta.setLore(List.of(si.lore, "", "§eKlick zum Kaufen"));
            is.setItemMeta(meta);
            inv.setItem(si.slot, is);
        }

        player.openInventory(inv);
    }

    public void handleClick(Player player, ItemStack clicked, int slot) {
        for (ShopItem si : ShopItem.values()) {
            if (si.slot != slot) continue;

            // Preis prüfen
            int has = countInInventory(player, si.currency);
            if (has < si.price) {
                player.sendMessage("§cNicht genug " + si.currency.name().toLowerCase().replace("_ingot","").replace("_", " ") + "!");
                return;
            }

            // Kaufen
            removeItems(player, si.currency, si.price);
            player.getInventory().addItem(new ItemStack(si.item, si.amount));
            player.sendMessage("§aGekauft: §f" + si.displayName.replaceAll("§.", ""));
            player.closeInventory();
            return;
        }
    }

    private int countInInventory(Player player, Material mat) {
        int count = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == mat) count += is.getAmount();
        }
        return count;
    }

    private void removeItems(Player player, Material mat, int amount) {
        int remaining = amount;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is == null || is.getType() != mat) continue;
            if (is.getAmount() <= remaining) {
                remaining -= is.getAmount();
                is.setAmount(0);
            } else {
                is.setAmount(is.getAmount() - remaining);
                remaining = 0;
            }
            if (remaining == 0) break;
        }
    }

    private void addSeparator(Inventory inv, int fromSlot, String name, Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of());
        pane.setItemMeta(meta);
        // Die erste Reihe jeder Kategorie mit dem Separator markieren
        inv.setItem(fromSlot + 8, pane);
    }
}
