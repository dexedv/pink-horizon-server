package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.GeneratorType;
import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop-GUI: Generatoren kaufen.
 * 6-reihiges Chest-Inventar mit einem Slot pro kaufbarem Generator-Typ.
 */
public class ShopGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "Generator-Shop";

    // Slot-Position für den Cobblestone-Generator (zentriert)
    private static final int[] GEN_SLOTS = {13};
    // Booster-Slots in Reihe 4
    private static final int[] BOOSTER_SLOTS = {29, 31, 33};

    public ShopGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MM.deserialize("<gold>" + TITLE));

        // Trennlinien
        ItemStack glass = filler(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        // Generator-Items (nur Cobblestone kaufbar, andere per Upgrade/Prestige freischaltbar)
        GeneratorType[] buyable = { GeneratorType.COBBLESTONE };
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());

        for (int i = 0; i < buyable.length; i++) {
            GeneratorType type = buyable[i];
            boolean canAfford = data != null && data.getMoney() >= type.getBuyPrice();
            inv.setItem(GEN_SLOTS[i], buildGenShopItem(type, canAfford));
        }

        // Booster-Items
        inv.setItem(BOOSTER_SLOTS[0], buildBoosterItem("x1.5 Booster (30 Min)", 25_000, Material.BLAZE_POWDER, 1.5, 30));
        inv.setItem(BOOSTER_SLOTS[1], buildBoosterItem("x2.0 Booster (60 Min)", 75_000, Material.BLAZE_ROD, 2.0, 60));
        inv.setItem(BOOSTER_SLOTS[2], buildBoosterItem("x3.0 Booster (30 Min)", 200_000, Material.FIRE_CHARGE, 3.0, 30));

        // Info-Item
        inv.setItem(49, buildInfoItem(data));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().title().equals(MM.deserialize("<gold>" + TITLE))) {
            event.setCancelled(true);
        } else {
            return;
        }

        int slot = event.getRawSlot();
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        // Generator kaufen?
        GeneratorType[] buyable = { GeneratorType.COBBLESTONE };
        for (int i = 0; i < GEN_SLOTS.length; i++) {
            if (slot == GEN_SLOTS[i]) {
                buyGenerator(player, data, buyable[i]);
                open(player);
                return;
            }
        }

        // Booster kaufen?
        if (slot == BOOSTER_SLOTS[0]) buyBooster(player, data, 1.5, 30, 25_000);
        if (slot == BOOSTER_SLOTS[1]) buyBooster(player, data, 2.0, 60, 75_000);
        if (slot == BOOSTER_SLOTS[2]) buyBooster(player, data, 3.0, 30, 200_000);
    }

    private void buyGenerator(Player player, PlayerData data, GeneratorType type) {
        if (type.getBuyPrice() == 0) {
            // Gratis (Cobblestone-Starter)
            player.getInventory().addItem(plugin.getGeneratorManager().createGeneratorItem(type, 1));
            player.sendMessage(MM.deserialize("<green>✔ " + type.getDisplayName() + " <green>erhalten!"));
            return;
        }
        if (data.getMoney() < type.getBuyPrice()) {
            player.sendMessage(MM.deserialize("<red>Nicht genug Geld! Benötigt: $"
                    + type.getBuyPrice() + " | Du hast: $" + data.getMoney()));
            return;
        }
        data.takeMoney(type.getBuyPrice());
        player.getInventory().addItem(plugin.getGeneratorManager().createGeneratorItem(type, 1));
        player.sendMessage(MM.deserialize("<green>✔ " + type.getDisplayName() + " <green>gekauft!"));
    }

    private void buyBooster(Player player, PlayerData data, double mult, int min, long cost) {
        var result = plugin.getBoosterManager().buyBooster(player, mult, min, cost);
        switch (result) {
            case SUCCESS -> open(player);
            case ALREADY_ACTIVE -> player.sendMessage(MM.deserialize("<red>Du hast bereits einen aktiven Booster!"));
            case NO_MONEY -> player.sendMessage(MM.deserialize("<red>Nicht genug Geld! Benötigt: $" + cost));
            default -> {}
        }
    }

    // ── Item-Builder ─────────────────────────────────────────────────────────

    private ItemStack buildGenShopItem(GeneratorType type, boolean canAfford) {
        ItemStack item = new ItemStack(type.getBlock());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(type.getDisplayName()));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Einkommen: <green>$" + (long) type.getBaseIncomePerSec() + "/s (Level 1)"));
        if (type.getBuyPrice() == 0) {
            lore.add(MM.deserialize("<green>KOSTENLOS"));
        } else {
            lore.add(MM.deserialize((canAfford ? "<green>" : "<red>") + "Preis: $" + type.getBuyPrice()));
        }
        lore.add(MM.deserialize("<gray>Upgrade-Basis: <yellow>$" + type.getBaseUpgradeCost()));
        lore.add(MM.deserialize(""));
        lore.add(MM.deserialize(canAfford ? "<yellow>Klick zum Kaufen!" : "<red>Zu teuer!"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildBoosterItem(String name, long cost, Material mat, double mult, int minutes) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<yellow>" + name));
        meta.lore(List.of(
                MM.deserialize("<gray>Multiplikator: <gold>x" + mult),
                MM.deserialize("<gray>Dauer: <white>" + minutes + " Minuten"),
                MM.deserialize("<gray>Preis: <green>$" + cost),
                MM.deserialize(""),
                MM.deserialize("<yellow>Klick zum Kaufen!")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildInfoItem(PlayerData data) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold>Dein Guthaben"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (data != null) {
            lore.add(MM.deserialize("<green>$" + data.getMoney()));
            lore.add(MM.deserialize("<gray>Prestige: <light_purple>" + data.getPrestige()));
            lore.add(MM.deserialize("<gray>Generatoren: <white>" + data.getGenerators().size()));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        item.setItemMeta(meta);
        return item;
    }
}
