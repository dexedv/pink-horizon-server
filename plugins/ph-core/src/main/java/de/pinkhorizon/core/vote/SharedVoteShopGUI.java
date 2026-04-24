package de.pinkhorizon.core.vote;

import de.pinkhorizon.core.PHCore;
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

/**
 * Gemeinsame VoteShop-GUI für alle Server.
 * Liest die Shop-Items aus PHCore's config.yml (Abschnitt "vote-shop").
 * Als Listener bei jedem Server-Plugin registrieren, das /voteshop anbietet.
 */
public class SharedVoteShopGUI implements Listener {

    private record ShopItem(int slot, Material material, String name,
                            int cost, List<String> lore, List<String> commands) {}

    private final Map<Integer, ShopItem> items = new HashMap<>();
    private String title;
    private int    rows;

    public SharedVoteShopGUI() {
        reload();
    }

    /** Items aus PHCore config.yml (re)laden. */
    public void reload() {
        items.clear();
        PHCore core = PHCore.getInstance();
        title = color(core.getConfig().getString("vote-shop.title",
            "&5&lVoteShop &8| &7Deine Coins: %coins%"));
        rows  = core.getConfig().getInt("vote-shop.rows", 3);

        ConfigurationSection sec = core.getConfig().getConfigurationSection("vote-shop.items");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection it = sec.getConfigurationSection(key);
            if (it == null) continue;
            int      slot  = it.getInt("slot", 0);
            Material mat   = Material.matchMaterial(it.getString("material", "PAPER"));
            if (mat == null) mat = Material.PAPER;
            String      name  = color(it.getString("name", key));
            int         cost  = it.getInt("cost", 1);
            List<String> lore = it.getStringList("lore").stream().map(this::color).toList();
            List<String> cmds = it.getStringList("commands");
            items.put(slot, new ShopItem(slot, mat, name, cost, lore, cmds));
        }
    }

    public void open(Player player) {
        int coins = SharedVoteCoinManager.getInstance().getCoins(player.getUniqueId());
        String t  = title.replace("%coins%", String.valueOf(coins));
        int size  = rows * 9;
        Inventory inv = Bukkit.createInventory(null, size,
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(t));

        // Dekorations-Rahmen
        ItemStack deco = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < size; i++) inv.setItem(i, deco);

        for (ShopItem si : items.values()) {
            if (si.slot() >= size) continue;
            List<String> lore = new ArrayList<>(si.lore());
            lore.add("");
            lore.add(color("&7Kosten: &d" + si.cost() + " VoteCoin(s)"));
            lore.add(color(coins >= si.cost() ? "&aKlicken zum Kaufen" : "&cZu wenig VoteCoins"));
            inv.setItem(si.slot(), buildItem(si.material(), si.name(), lore));
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;
        String rawTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(event.getView().title());
        if (!rawTitle.contains("VoteShop")) return;

        event.setCancelled(true);
        ShopItem si = items.get(event.getRawSlot());
        if (si == null) return;

        int coins = SharedVoteCoinManager.getInstance().getCoins(player.getUniqueId());
        if (coins < si.cost()) {
            player.sendMessage(color("&cZu wenig VoteCoins! Du hast &f" + coins
                + " &cvon &f" + si.cost() + " &cbenötigten."));
            player.closeInventory();
            return;
        }
        if (!SharedVoteCoinManager.getInstance().removeCoins(player.getUniqueId(), si.cost())) {
            player.sendMessage(color("&cFehler beim Kaufen. Bitte erneut versuchen."));
            player.closeInventory();
            return;
        }
        for (String cmd : si.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                cmd.replace("[player]", player.getName()));
        }
        int remaining = SharedVoteCoinManager.getInstance().getCoins(player.getUniqueId());
        player.sendMessage(color("&d&l[VoteShop] &7Gekauft: &d"
            + si.name().replaceAll("§.", "")
            + " &8(Verbleibend: " + remaining + " VoteCoins)"));
        player.closeInventory();
    }

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().deserialize(name));
        meta.lore(lore.stream()
            .<net.kyori.adventure.text.Component>map(l ->
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(l))
            .toList());
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                          org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private String color(String s) { return s == null ? "" : s.replace("&", "\u00a7"); }
}
