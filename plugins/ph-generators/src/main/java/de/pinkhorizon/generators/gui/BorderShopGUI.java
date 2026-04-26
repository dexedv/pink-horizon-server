package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.MoneyManager;
import de.pinkhorizon.generators.managers.WorldBorderManager;
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
 * Shop zum Kaufen von Insel-Border-Erweiterungen.
 */
public class BorderShopGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "✦ Insel-Grenze erweitern";

    // Materialien für die Tier-Items (Reihenfolge = Tiers)
    private static final Material[] TIER_MATS = {
            Material.CYAN_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.ORANGE_CONCRETE,
            Material.RED_CONCRETE,
            Material.PURPLE_CONCRETE,
    };

    public BorderShopGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, MM.deserialize("<cyan>" + TITLE));

        ItemStack glass = filler();
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        inv.setItem(4, buildInfoItem(data));

        long[][] tiers = WorldBorderManager.TIERS;
        int currentIdx = plugin.getWorldBorderManager().getCurrentTierIndex(data.getBorderSize());

        for (int i = 0; i < tiers.length; i++) {
            int size = (int) tiers[i][0];
            long cost = tiers[i][1];
            boolean unlocked = i <= currentIdx;
            boolean isCurrent = i == currentIdx;
            boolean isNext    = i == currentIdx + 1;

            inv.setItem(9 + i, buildTierItem(size, cost, unlocked, isCurrent, isNext, data, TIER_MATS[i]));
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(MM.deserialize("<cyan>" + TITLE))) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 9 || slot > 14) return;

        int tierIndex = slot - 9;
        long[][] tiers = WorldBorderManager.TIERS;
        if (tierIndex >= tiers.length) return;

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        int currentIdx = plugin.getWorldBorderManager().getCurrentTierIndex(data.getBorderSize());

        // Nur die direkt nächste Stufe kaufbar
        if (tierIndex != currentIdx + 1) {
            if (tierIndex <= currentIdx) {
                player.sendMessage(MM.deserialize("<gray>Diese Stufe ist bereits freigeschaltet."));
            } else {
                player.sendMessage(MM.deserialize("<red>Schalte zuerst die vorherige Stufe frei!"));
            }
            return;
        }

        WorldBorderManager.ExpandResult result = plugin.getWorldBorderManager().expand(data);
        switch (result) {
            case SUCCESS -> {
                player.sendMessage(MM.deserialize(
                        "<green>✔ Insel-Grenze auf <white>" + data.getBorderSize()
                                + "×" + data.getBorderSize() + " <green>erweitert!"));
                open(player); // Refresh
            }
            case NO_MONEY -> player.sendMessage(MM.deserialize(
                    "<red>Nicht genug Geld! Benötigt: $"
                            + MoneyManager.formatMoney(tiers[tierIndex][1])
                            + " | Du hast: $" + MoneyManager.formatMoney(data.getMoney())));
            case ALREADY_MAX -> player.sendMessage(MM.deserialize(
                    "<gold>Du hast bereits die maximale Grenze erreicht!"));
            default -> {}
        }
    }

    // ── Item-Builder ─────────────────────────────────────────────────────────

    private ItemStack buildTierItem(int size, long cost, boolean unlocked, boolean isCurrent,
                                     boolean isNext, PlayerData data, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String status;
        if (isCurrent)   status = "<green><bold>▶ AKTIV</bold></green>";
        else if (unlocked) status = "<gray>✔ Freigeschaltet";
        else if (isNext) status = "<yellow>Kaufbar";
        else             status = "<dark_gray>🔒 Gesperrt";

        meta.displayName(MM.deserialize("<white><bold>" + size + "×" + size + " Grenze</bold>"));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Spielfläche: <white>" + size + "×" + size + " Blöcke"));
        lore.add(MM.deserialize(""));
        lore.add(MM.deserialize("<gray>Status: " + status));
        if (!unlocked) {
            boolean canAfford = data.getMoney() >= cost;
            lore.add(MM.deserialize((canAfford ? "<green>" : "<red>")
                    + "Kosten: $" + MoneyManager.formatMoney(cost)));
        }
        if (isNext) {
            lore.add(MM.deserialize(""));
            lore.add(MM.deserialize("<yellow>Klick → Kaufen"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildInfoItem(PlayerData data) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<aqua><bold>Deine Insel-Grenze</bold>"));
        long[] next = plugin.getWorldBorderManager().getNextTier(data.getBorderSize());
        meta.lore(List.of(
                MM.deserialize("<gray>Aktuelle Größe: <white>" + data.getBorderSize()
                        + "×" + data.getBorderSize()),
                MM.deserialize("<gray>Guthaben: <green>$" + MoneyManager.formatMoney(data.getMoney())),
                next != null
                        ? MM.deserialize("<gray>Nächste Stufe: <yellow>" + (int)next[0] + "×"
                                + (int)next[0] + " <gray>für <green>$" + MoneyManager.formatMoney(next[1]))
                        : MM.deserialize("<gold>Maximale Größe erreicht!")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gray> "));
        item.setItemMeta(meta);
        return item;
    }
}
