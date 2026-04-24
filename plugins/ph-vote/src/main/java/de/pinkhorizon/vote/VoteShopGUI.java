package de.pinkhorizon.vote;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VoteShopGUI implements Listener {

    private final PHVote plugin;

    // slot → ShopItem für Klick-Erkennung
    private record ShopItem(int slot, Material material, String name, int cost,
                            List<String> lore, List<String> commands) {}

    private final Map<Integer, ShopItem> shopItems = new HashMap<>();

    public VoteShopGUI(PHVote plugin) {
        this.plugin = plugin;
        loadItems();
    }

    private void loadItems() {
        shopItems.clear();
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            ConfigurationSection sec = items.getConfigurationSection(key);
            if (sec == null) continue;

            int      slot     = sec.getInt("slot", 0);
            Material mat      = Material.matchMaterial(sec.getString("material", "PAPER"));
            String   name     = color(sec.getString("name", key));
            int      cost     = sec.getInt("cost", 1);
            List<String> lore = sec.getStringList("lore").stream().map(this::color).toList();
            List<String> cmds = sec.getStringList("commands");

            if (mat == null) mat = Material.PAPER;
            shopItems.put(slot, new ShopItem(slot, mat, name, cost, lore, cmds));
        }
    }

    public void open(Player player) {
        int rows  = plugin.getConfig().getInt("shop.rows", 3);
        int size  = rows * 9;
        int coins = plugin.getVoteCoinManager().getCoins(player.getUniqueId());

        String title = color(plugin.getConfig()
            .getString("shop.title", "&5&lVoteShop")
            .replace("%coins%", String.valueOf(coins)));

        Inventory inv = Bukkit.createInventory(null, size, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(title));

        // Dekorations-Rahmen (graues Glas)
        ItemStack deco = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < size; i++) inv.setItem(i, deco);

        // Shop-Items einfügen
        for (ShopItem si : shopItems.values()) {
            if (si.slot() >= size) continue;
            List<String> lore = new ArrayList<>(si.lore());
            lore.add("");
            lore.add(color("&7Kosten: &d" + si.cost() + " VoteCoin(s)"));
            lore.add(color(coins >= si.cost() ? "&aKlicke zum Kaufen" : "&cZu wenig VoteCoins"));
            inv.setItem(si.slot(), buildItem(si.material(), si.name(), lore));
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;

        // Prüfen ob es unser Shop ist (über Titel)
        String rawTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .serialize(event.getView().title());
        if (!rawTitle.contains("VoteShop")) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        ShopItem si = shopItems.get(slot);
        if (si == null) return;

        int coins = plugin.getVoteCoinManager().getCoins(player.getUniqueId());
        if (coins < si.cost()) {
            player.sendMessage(color("&cDu hast zu wenig VoteCoins! Du hast &f" + coins
                + " &cvon &f" + si.cost() + " &cbenötigten."));
            player.closeInventory();
            return;
        }

        // Coins abziehen
        boolean ok = plugin.getVoteCoinManager().removeCoins(player.getUniqueId(), si.cost());
        if (!ok) {
            player.sendMessage(color("&cFehler beim Kaufen. Versuche es erneut."));
            player.closeInventory();
            return;
        }

        // Befehle ausführen
        for (String cmd : si.commands()) {
            String resolved = cmd.replace("[player]", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }

        int remaining = plugin.getVoteCoinManager().getCoins(player.getUniqueId());
        player.sendMessage(color("&d&l[VoteShop] &7Du hast &d" + si.name().replaceAll("§.", "")
            + " &7gekauft! &8(Verbleibend: " + remaining + " VoteCoins)"));
        player.closeInventory();
    }

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().deserialize(name));
        List<net.kyori.adventure.text.Component> loreComponents = lore.stream()
            .<net.kyori.adventure.text.Component>map(l -> net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(l))
            .toList();
        meta.lore(loreComponents);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                          org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private String color(String s) {
        return s.replace("&", "\u00a7");
    }
}
