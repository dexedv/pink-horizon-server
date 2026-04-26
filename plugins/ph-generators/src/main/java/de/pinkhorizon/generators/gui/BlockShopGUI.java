package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.MoneyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
 * Block-Shop GUI: Spieler kaufen Erweiterungsblöcke mit Generators-Geld.
 * Konfiguration in config.yml unter "block-shop".
 */
public class BlockShopGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "⚒ Block-Shop";

    public BlockShopGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("block-shop");
        if (section == null) {
            player.sendMessage(MM.deserialize("<red>Block-Shop ist nicht konfiguriert!"));
            return;
        }

        List<String> keys = new ArrayList<>(section.getKeys(false));
        int size = Math.min(54, (int) Math.ceil(keys.size() / 9.0) * 9 + 9);
        size = Math.max(size, 27);

        Inventory inv = Bukkit.createInventory(null, size, MM.deserialize("<gold>" + TITLE));

        // Füller
        ItemStack filler = filler();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        // Info-Item oben Mitte
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        long balance = data != null ? data.getMoney() : 0;
        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(MM.deserialize("<gold>Dein Guthaben"));
        infoMeta.lore(List.of(MM.deserialize("<green>$" + MoneyManager.formatMoney(balance))));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Shop-Items ab Slot 9
        int slot = 9;
        for (String key : keys) {
            if (slot >= size) break;
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) continue;

            String matStr = item.getString("material", "STONE");
            Material mat = Material.matchMaterial(matStr);
            if (mat == null) continue;

            String name = item.getString("name", "<white>" + matStr);
            long price = item.getLong("price", 100);
            int amount = item.getInt("amount", 64);

            inv.setItem(slot++, buildShopItem(mat, name, price, amount, balance));
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(MM.deserialize("<gold>" + TITLE))) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 9) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        if (!clicked.getItemMeta().hasLore()) return;

        // Preis aus Lore auslesen
        long price = extractPrice(clicked);
        if (price < 0) return;

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        if (!data.takeMoney(price)) {
            player.sendMessage(MM.deserialize("<red>Nicht genug Geld! Benötigt: $"
                    + MoneyManager.formatMoney(price)
                    + " | Du hast: $" + MoneyManager.formatMoney(data.getMoney())));
            return;
        }

        // Item-Menge aus Name herauslesen (erstes Wort der Lore mit "x")
        int amount = extractAmount(clicked);
        Material mat = clicked.getType();

        ItemStack give = new ItemStack(mat, amount);
        player.getInventory().addItem(give).forEach((i, leftover) ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        player.sendMessage(MM.deserialize("<green>✔ Gekauft: <white>" + amount + "x "
                + mat.name().toLowerCase().replace("_", " ")
                + " <green>für <yellow>$" + MoneyManager.formatMoney(price)));

        open(player); // GUI aktualisieren
    }

    private ItemStack buildShopItem(Material mat, String name, long price, int amount, long balance) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        boolean canAfford = balance >= price;
        meta.lore(List.of(
                MM.deserialize("<gray>Menge: <white>" + amount + "x"),
                MM.deserialize((canAfford ? "<green>" : "<red>") + "Preis: $" + MoneyManager.formatMoney(price)),
                MM.deserialize(""),
                MM.deserialize("<yellow>Linksklick → Kaufen")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private long extractPrice(ItemStack item) {
        try {
            for (net.kyori.adventure.text.Component line : item.getItemMeta().lore()) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(line);
                if (plain.contains("Preis: $")) {
                    String num = plain.replaceAll("[^0-9]", "");
                    return Long.parseLong(num);
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private int extractAmount(ItemStack item) {
        try {
            for (net.kyori.adventure.text.Component line : item.getItemMeta().lore()) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(line);
                if (plain.startsWith("Menge:")) {
                    String num = plain.replaceAll("[^0-9]", "");
                    return Integer.parseInt(num);
                }
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gray> "));
        item.setItemMeta(meta);
        return item;
    }
}
