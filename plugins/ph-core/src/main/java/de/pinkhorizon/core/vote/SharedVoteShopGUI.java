package de.pinkhorizon.core.vote;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemeinsame VoteShop-GUI für alle Server.
 * Liest die Shop-Items aus vote-shop.yml im Plugin-Data-Ordner.
 */
public class SharedVoteShopGUI implements Listener {

    private record ShopItem(int slot, Material material, String name,
                            int cost, List<String> lore, List<String> commands) {}

    private final Map<Integer, ShopItem>   items       = new HashMap<>();
    /** Decorative items (border slots + named decorations). No click action. */
    private final Map<Integer, ItemStack>  decorations = new HashMap<>();

    private final JavaPlugin plugin;
    private String   title;
    private int      rows;
    private Material fillMaterial = Material.GRAY_STAINED_GLASS_PANE;

    public SharedVoteShopGUI(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveResource("vote-shop.yml", false);
        reload();
    }

    public void reload() {
        items.clear();
        decorations.clear();

        File file = new File(plugin.getDataFolder(), "vote-shop.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        title = color(cfg.getString("title", "&5&lVoteShop &8| &7Deine Coins: %coins%"));
        rows  = cfg.getInt("rows", 3);

        // Fill material
        Material fm = Material.matchMaterial(cfg.getString("fill-material", "GRAY_STAINED_GLASS_PANE"));
        fillMaterial = fm != null ? fm : Material.GRAY_STAINED_GLASS_PANE;

        // Border slots: "slot": "MATERIAL_NAME"
        ConfigurationSection borderCfg = cfg.getConfigurationSection("border-slots");
        if (borderCfg != null) {
            for (String key : borderCfg.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    Material mat = Material.matchMaterial(borderCfg.getString(key, ""));
                    if (mat != null) decorations.put(slot, buildItem(mat, " ", List.of()));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Named decorations: slot + material + name + lore (no purchase)
        ConfigurationSection decoCfg = cfg.getConfigurationSection("decorations");
        if (decoCfg != null) {
            for (String key : decoCfg.getKeys(false)) {
                ConfigurationSection d = decoCfg.getConfigurationSection(key);
                if (d == null) continue;
                int slot = d.getInt("slot", -1);
                if (slot < 0) continue;
                Material mat = Material.matchMaterial(d.getString("material", "PAPER"));
                if (mat == null) mat = Material.PAPER;
                String name  = color(d.getString("name", ""));
                List<String> lore = d.getStringList("lore").stream().map(this::color).toList();
                decorations.put(slot, buildItem(mat, name, lore));
            }
        }

        // Shop items
        ConfigurationSection sec = cfg.getConfigurationSection("items");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection it = sec.getConfigurationSection(key);
                if (it == null) continue;
                int      slot  = it.getInt("slot", 0);
                Material mat   = Material.matchMaterial(it.getString("material", "PAPER"));
                if (mat == null) mat = Material.PAPER;
                String       name  = color(it.getString("name", key));
                int          cost  = it.getInt("cost", 1);
                List<String> lore  = it.getStringList("lore").stream().map(this::color).toList();
                List<String> cmds  = it.getStringList("commands");
                items.put(slot, new ShopItem(slot, mat, name, cost, lore, cmds));
            }
        }
    }

    public void open(Player player) {
        int coins = SharedVoteCoinManager.getInstance().getCoins(player.getUniqueId());
        String t  = title.replace("%coins%", String.valueOf(coins));
        int size  = rows * 9;

        Inventory inv = Bukkit.createInventory(null, size,
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(t));

        // 1. Fill all slots with fill material
        ItemStack fill = buildItem(fillMaterial, " ", List.of());
        for (int i = 0; i < size; i++) inv.setItem(i, fill);

        // 2. Place border/decoration items
        for (Map.Entry<Integer, ItemStack> e : decorations.entrySet()) {
            if (e.getKey() < size) inv.setItem(e.getKey(), e.getValue());
        }

        // 3. Place shop items on top
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

        // Decoration or border slots → no action
        if (decorations.containsKey(event.getRawSlot())) return;

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
