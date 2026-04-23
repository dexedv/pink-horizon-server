package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import de.pinkhorizon.smash.managers.TalentManager.TalentType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TalentGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** Slots for the 5 talents in the 27-slot inventory */
    private static final int[] TALENT_SLOTS = {10, 12, 14, 19, 21};

    private final PHSmash plugin;

    public TalentGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Inventory holder
    // -------------------------------------------------------------------------

    public static class TalentHolder implements InventoryHolder {
        private final UUID playerUuid;
        TalentHolder(UUID playerUuid) { this.playerUuid = playerUuid; }
        public UUID getPlayerUuid() { return playerUuid; }
        @Override public Inventory getInventory() { return null; }
    }

    // -------------------------------------------------------------------------
    // Open / fill
    // -------------------------------------------------------------------------

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(
            new TalentHolder(player.getUniqueId()),
            27,
            LEGACY.deserialize("§5§l✦ Talente §8– §7Boss-Kerne ausgeben"));
        fillGui(inv, player);
        player.openInventory(inv);
    }

    private void fillGui(Inventory inv, Player player) {
        UUID uuid = player.getUniqueId();

        // Gray glass filler
        ItemStack pane = makePure(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 0; s < 27; s++) inv.setItem(s, pane);

        // Slot 4: Boss Core display
        int coreCount = plugin.getLootManager().getQuantity(uuid, LootItem.BOSS_CORE);
        ItemStack coreItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta coreMeta = coreItem.getItemMeta();
        coreMeta.displayName(LEGACY.deserialize("§5§lBoss-Kerne"));
        coreMeta.lore(List.of(
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Vorrat: §5" + coreCount + " Boss-Kerne"),
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Gib deine Boss-Kerne für Talente aus.")
        ));
        coreItem.setItemMeta(coreMeta);
        inv.setItem(4, coreItem);

        // Talent items
        TalentType[] types = TalentType.values();
        for (int i = 0; i < TALENT_SLOTS.length; i++) {
            inv.setItem(TALENT_SLOTS[i], buildTalentItem(player, types[i], coreCount));
        }

        // Close button (slot 22)
        inv.setItem(22, buildClose());
    }

    private ItemStack buildTalentItem(Player player, TalentType type, int coresOwned) {
        UUID uuid     = player.getUniqueId();
        int  current  = plugin.getTalentManager().getLevel(uuid, type);
        boolean maxed = current >= type.maxLevel;
        int  cost     = type.nextCost(current);
        boolean canAfford = !maxed && coresOwned >= cost;

        Material mat = switch (type) {
            case TREASURE_HUNTER -> Material.EMERALD;
            case IRON_HEART      -> Material.GOLDEN_APPLE;
            case FAST_HANDS      -> Material.FEATHER;
            case COIN_MASTER     -> Material.GOLD_INGOT;
            case BOSS_SLAYER     -> Material.NETHERITE_SWORD;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        String name = type.displayName + (maxed ? " §a(MAX)" : "");
        meta.displayName(LEGACY.deserialize(name));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize(type.effectDesc));
        lore.add(LEGACY.deserialize("§7Level: §f" + bar(current, type.maxLevel)));
        lore.add(LEGACY.deserialize("§7Level: §f" + current + "§8/§f" + type.maxLevel));
        lore.add(LEGACY.deserialize("§8─────────────────────"));

        if (!maxed) {
            lore.add(LEGACY.deserialize("§7Kosten: §5" + cost + " §7Boss-Kerne"));
            String haveColor = coresOwned >= cost ? "§a" : "§c";
            lore.add(LEGACY.deserialize("§7Besitzt: " + haveColor + coresOwned + " Boss-Kerne"));
            lore.add(LEGACY.deserialize("§8─────────────────────"));
            if (canAfford) {
                lore.add(LEGACY.deserialize("§a▶ Klicken zum Upgraden!"));
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(LEGACY.deserialize("§c✗ Nicht genug Boss-Kerne"));
            }
        } else {
            lore.add(LEGACY.deserialize("§a✔ Maximales Level erreicht!"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Click handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TalentHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        // Close button
        if (slot == 22) {
            player.closeInventory();
            return;
        }

        // Talent slots
        TalentType[] types = TalentType.values();
        for (int i = 0; i < TALENT_SLOTS.length; i++) {
            if (slot == TALENT_SLOTS[i]) {
                TalentType type = types[i];
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getTalentManager().tryUpgrade(player, type);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            // Refresh GUI
                            fillGui(event.getInventory(), player);
                            // Trigger scoreboard update
                            plugin.getScoreboardManager().update(player);
                        }
                    });
                });
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ItemStack makePure(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildClose() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§cSchließen"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Renders a 15-character progress bar.
     */
    private static String bar(int current, int max) {
        int len    = 15;
        int filled = max > 0 ? Math.min(len, (int) Math.round(len * (double) current / max)) : 0;
        String col = filled >= len ? "§a" : filled >= len / 2 ? "§e" : "§c";
        return col + "█".repeat(filled) + "§8" + "█".repeat(len - filled);
    }
}
