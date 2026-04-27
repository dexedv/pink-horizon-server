package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.MiningBlockManager;
import de.pinkhorizon.generators.managers.MoneyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * GUI für Mining-Block und Mining-Spitzhacke Upgrades.
 * Öffnet sich via Sneak + Rechtsklick auf den Mining-Block oder mit der Mining-Spitzhacke.
 *
 * Layout (27 Slots):
 *   Row 0: [G][G][BLOCK-INFO][G][SHARDS][G][PICK-INFO][G][G]
 *   Row 1: [G][G][BLOCK-UP ][G][  G   ][G][PICK-UP  ][G][G]
 *   Row 2: [G][G][G        ][G][CLOSE ][G][G         ][G][G]
 */
public class MiningUpgradeGUI implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "⛏ Mining-Upgrades";

    private final PHGenerators plugin;

    public MiningUpgradeGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    // ── Öffnen ───────────────────────────────────────────────────────────────

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, MM.deserialize("<light_purple>" + TITLE));
        ItemStack fill = filler();
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);

        int blockLevel   = data.getMiningLevel();
        int pickLevel    = data.getMiningPickaxeLevel();
        int maxBlockLvl  = plugin.getConfig().getInt("mining-block.max-level", 100);
        int maxPickLvl   = plugin.getConfig().getInt("mining-block.pickaxe-max-level", 50);
        int shards       = data.getShards();

        long baseMoney   = plugin.getConfig().getLong("mining-block.base-money", 5);
        double pickMult  = 1.0 + (pickLevel - 1) * 0.15;
        long incomeHit   = (long) (baseMoney * blockLevel * pickMult);

        int shardsBlock  = blockLevel * plugin.getConfig().getInt("mining-block.upgrade-shards", 5);
        int shardsPick   = plugin.getMiningBlockManager().shardsNeededForPickaxe(data);
        double shardPct  = (plugin.getConfig().getDouble("mining-block.shard-chance", 0.10)
                + (blockLevel * 0.005) + (pickLevel * 0.01)) * 100;

        // Slot 2 – Block-Info
        inv.setItem(2, buildInfo(
                Material.AMETHYST_BLOCK,
                "<light_purple><bold>⛏ Mining-Block</bold>",
                List.of(
                        "<gray>Level: <white>" + blockLevel + " <dark_gray>/ " + maxBlockLvl,
                        "<gray>Geld/Schlag: <green>$" + MoneyManager.formatMoney(incomeHit),
                        "<gray>Shard-Chance: <light_purple>" + String.format("%.1f", shardPct) + "%"
                )
        ));

        // Slot 4 – Shards
        ItemStack shardsItem = plugin.getMiningBlockManager().createShardItem(1);
        ItemMeta sm = shardsItem.getItemMeta();
        sm.displayName(MM.deserialize("<light_purple>✦ Mining-Shards"));
        sm.lore(List.of(MM.deserialize("<gray>Deine Shards: <white>" + shards)));
        shardsItem.setItemMeta(sm);
        inv.setItem(4, shardsItem);

        // Slot 6 – Spitzhacke-Info
        double coinChance = (0.01 + (pickLevel - 1) / (double)(maxPickLvl - 1) * 0.09) * 100;
        ItemStack pickInfo = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta pm = pickInfo.getItemMeta();
        pm.displayName(MM.deserialize("<aqua><bold>⛏ Mining-Spitzhacke</bold>"));
        pm.lore(List.of(
                MM.deserialize("<gray>Level: <white>" + pickLevel + " <dark_gray>/ " + maxPickLvl),
                MM.deserialize("<gray>Multiplikator: <green>x" + String.format("%.2f", pickMult)),
                MM.deserialize("<gray>Shard-Bonus: <light_purple>+" + pickLevel + "%"),
                MM.deserialize("<gray>Coin-Drop: <gold>" + String.format("%.1f", coinChance) + "% <dark_gray>(1–$10k)")
        ));
        pm.setUnbreakable(true);
        pm.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        pickInfo.setItemMeta(pm);
        inv.setItem(6, pickInfo);

        // Slot 11 – Block-Upgrade
        if (blockLevel >= maxBlockLvl) {
            inv.setItem(11, maxItem("<light_purple>Block: MAX erreicht!"));
        } else {
            inv.setItem(11, upgradeBtn(
                    shards >= shardsBlock,
                    "<light_purple>Block upgraden",
                    "Lvl " + blockLevel + " → " + (blockLevel + 1),
                    shardsBlock, shards
            ));
        }

        // Slot 15 – Pickaxe-Upgrade
        if (pickLevel >= maxPickLvl) {
            inv.setItem(15, maxItem("<aqua>Spitzhacke: MAX erreicht!"));
        } else {
            inv.setItem(15, upgradeBtn(
                    shards >= shardsPick,
                    "<aqua>Spitzhacke upgraden",
                    "Lvl " + pickLevel + " → " + (pickLevel + 1),
                    shardsPick, shards
            ));
        }

        // Slot 22 – Schließen
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(MM.deserialize("<red>Schließen"));
        close.setItemMeta(cm);
        inv.setItem(22, close);

        player.openInventory(inv);
    }

    // ── Click-Handler ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(MM.deserialize("<light_purple>" + TITLE))) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String name = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        if (name.contains("Schließen")) {
            player.closeInventory();
            return;
        }

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        if (name.contains("Block upgraden")) {
            MiningBlockManager.UpgradeResult res = plugin.getMiningBlockManager().upgrade(player, data);
            sendResult(player, res, "Block");
            if (res == MiningBlockManager.UpgradeResult.SUCCESS) {
                // GUI neu öffnen mit aktualisierten Werten
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 1L);
            }
        } else if (name.contains("Spitzhacke upgraden")) {
            MiningBlockManager.UpgradeResult res = plugin.getMiningBlockManager().upgradePickaxe(player, data);
            sendResult(player, res, "Spitzhacke");
            if (res == MiningBlockManager.UpgradeResult.SUCCESS) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 1L);
            }
        }
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private void sendResult(Player player, MiningBlockManager.UpgradeResult result, String type) {
        switch (result) {
            case SUCCESS          -> player.sendMessage(MM.deserialize("<green>✦ " + type + " auf Level " +
                    (type.equals("Block")
                            ? plugin.getPlayerDataMap().get(player.getUniqueId()).getMiningLevel()
                            : plugin.getPlayerDataMap().get(player.getUniqueId()).getMiningPickaxeLevel())
                    + " geupdated!"));
            case MAX_LEVEL        -> player.sendMessage(MM.deserialize("<yellow>✦ " + type + " ist bereits auf Max-Level!"));
            case NOT_ENOUGH_SHARDS -> player.sendMessage(MM.deserialize("<red>✦ Nicht genug Shards für das " + type + "-Upgrade!"));
        }
    }

    private ItemStack buildInfo(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(loreLines.stream().map(MM::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack upgradeBtn(boolean canAfford, String name, String levelInfo,
                                  int needed, int have) {
        ItemStack item = new ItemStack(canAfford ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(List.of(
                MM.deserialize("<gray>" + levelInfo),
                MM.deserialize(""),
                MM.deserialize("<gray>Kosten: <light_purple>" + needed + " Shards"),
                MM.deserialize("<gray>Vorhanden: " + (canAfford ? "<green>" : "<red>") + have),
                MM.deserialize(""),
                MM.deserialize(canAfford ? "<yellow>▶ Klick zum Upgraden" : "<red>✗ Nicht genug Shards")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack maxItem(String label) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(label));
        meta.lore(List.of(MM.deserialize("<gray>Maximales Level erreicht!")));
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
