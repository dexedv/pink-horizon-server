package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.LootManager.LootItem;
import de.pinkhorizon.smash.managers.RuneManager.RuneType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RuneGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final PHSmash plugin;

    public RuneGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Inventory holder
    // -------------------------------------------------------------------------

    public static class RuneHolder implements InventoryHolder {
        private final UUID playerUuid;
        RuneHolder(UUID playerUuid) { this.playerUuid = playerUuid; }
        public UUID getPlayerUuid() { return playerUuid; }
        @Override public Inventory getInventory() { return null; }
    }

    // -------------------------------------------------------------------------
    // Open / fill
    // -------------------------------------------------------------------------

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(
            new RuneHolder(player.getUniqueId()),
            27,
            LEGACY.deserialize("§6§l☆ Runen §8– §7Schmieden & Aktivieren"));
        fillGui(inv, player);
        player.openInventory(inv);
    }

    private void fillGui(Inventory inv, Player player) {
        UUID uuid = player.getUniqueId();

        // Gray glass filler
        ItemStack pane = makePure(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 0; s < 27; s++) inv.setItem(s, pane);

        // Slot 4: Info item
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta  infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(LEGACY.deserialize("§6§lRunen-Schmiede"));
        infoMeta.lore(List.of(
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Runen verleihen dir temporäre Boni"),
            LEGACY.deserialize("§7für die nächsten §f3 Bosse§7."),
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Jeder besiegte Boss verbraucht"),
            LEGACY.deserialize("§7eine Ladung aller aktiven Runen."),
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Schmieden gibt §f+3 Ladungen§7.")
        ));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Rune items
        inv.setItem(10, buildRuneItem(player, RuneType.WAR_RUNE,    uuid));
        inv.setItem(13, buildRuneItem(player, RuneType.SHIELD_RUNE, uuid));
        inv.setItem(16, buildRuneItem(player, RuneType.LUCK_RUNE,   uuid));

        // Close button
        inv.setItem(22, buildClose());
    }

    private ItemStack buildRuneItem(Player player, RuneType type, UUID uuid) {
        int charges = plugin.getRuneManager().getCharges(uuid, type);
        boolean active = charges > 0;

        ItemStack item = new ItemStack(type.icon);
        ItemMeta  meta = item.getItemMeta();

        String nameColor = active ? "§a" : "§c";
        meta.displayName(LEGACY.deserialize(nameColor + type.displayName));

        // Material costs display
        String materialLine = getMaterialLine(player, type, uuid);

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize(type.effectDesc));
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        String chargeColor = active ? "§a" : "§c";
        lore.add(LEGACY.deserialize("§7Ladungen: " + chargeColor + charges + "§8/§f3"));
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize(type.costDesc));
        lore.add(LEGACY.deserialize(materialLine));
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize("§aKlicken zum Schmieden"));
        lore.add(LEGACY.deserialize("§7Schmieden gibt +3 Ladungen"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String getMaterialLine(Player player, RuneType type, UUID uuid) {
        return switch (type) {
            case WAR_RUNE -> {
                int have = plugin.getLootManager().getQuantity(uuid, LootItem.IRON_FRAGMENT);
                String col = have >= 5 ? "§a" : "§c";
                yield "§7Eisen-Splitter: " + col + have + "§8/§f5";
            }
            case SHIELD_RUNE -> {
                int have = plugin.getLootManager().getQuantity(uuid, LootItem.GOLD_FRAGMENT);
                String col = have >= 5 ? "§a" : "§c";
                yield "§7Gold-Splitter: " + col + have + "§8/§f5";
            }
            case LUCK_RUNE -> {
                int have = plugin.getLootManager().getQuantity(uuid, LootItem.DIAMOND_SHARD);
                String col = have >= 2 ? "§a" : "§c";
                yield "§7Boss-Kristalle: " + col + have + "§8/§f2";
            }
        };
    }

    // -------------------------------------------------------------------------
    // Click handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RuneHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        // Close button
        if (slot == 22) {
            player.closeInventory();
            return;
        }

        // Rune craft slots
        RuneType type = switch (slot) {
            case 10 -> RuneType.WAR_RUNE;
            case 13 -> RuneType.SHIELD_RUNE;
            case 16 -> RuneType.LUCK_RUNE;
            default -> null;
        };

        if (type != null) {
            RuneType finalType = type;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getRuneManager().craftRune(player, finalType, plugin);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) open(player);
                });
            });
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
}
