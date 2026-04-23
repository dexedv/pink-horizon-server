package de.pinkhorizon.smash.gui;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.managers.DailyChallengeManager.ChallengeType;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DailyChallengeGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Slots for the 3 daily challenges (centered in row 2 of 54-slot GUI) */
    private static final int[] CHALLENGE_SLOTS = {20, 22, 24};

    private final PHSmash plugin;

    public DailyChallengeGui(PHSmash plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Inventory holder
    // -------------------------------------------------------------------------

    public static class DailyHolder implements InventoryHolder {
        private final UUID playerUuid;
        DailyHolder(UUID playerUuid) { this.playerUuid = playerUuid; }
        public UUID getPlayerUuid() { return playerUuid; }
        @Override public Inventory getInventory() { return null; }
    }

    // -------------------------------------------------------------------------
    // Open / fill
    // -------------------------------------------------------------------------

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(
            new DailyHolder(player.getUniqueId()),
            54,
            LEGACY.deserialize("§a§l✦ Tägliche Herausforderungen"));
        fillGui(inv, player);
        player.openInventory(inv);
    }

    private void fillGui(Inventory inv, Player player) {
        UUID uuid = player.getUniqueId();

        // Gray glass filler
        ItemStack pane = makePure(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 0; s < 54; s++) inv.setItem(s, pane);

        // Row 0: dark border
        ItemStack darkPane = makePure(Material.BLACK_STAINED_GLASS_PANE);
        for (int s = 0; s < 9; s++) inv.setItem(s, darkPane);

        // Slot 4: Date display item
        String dateStr = LocalDate.now().format(DATE_FMT);
        ItemStack dateItem = new ItemStack(Material.CLOCK);
        ItemMeta  dateMeta = dateItem.getItemMeta();
        dateMeta.displayName(LEGACY.deserialize("§e§lHerausforderungen – §f" + dateStr));
        dateMeta.lore(List.of(
            LEGACY.deserialize("§8─────────────────────"),
            LEGACY.deserialize("§7Täglich um Mitternacht neu"),
            LEGACY.deserialize("§8─────────────────────")
        ));
        dateItem.setItemMeta(dateMeta);
        inv.setItem(4, dateItem);

        // Challenge items
        List<ChallengeType> todaysChallenges = plugin.getDailyChallengeManager().getTodaysChallenges();
        for (int i = 0; i < CHALLENGE_SLOTS.length; i++) {
            if (i < todaysChallenges.size()) {
                ChallengeType type = todaysChallenges.get(i);
                inv.setItem(CHALLENGE_SLOTS[i], buildChallengeItem(uuid, type, i));
            }
        }

        // Close button (slot 49)
        inv.setItem(49, buildClose());
    }

    private ItemStack buildChallengeItem(UUID uuid, ChallengeType type, int slotIndex) {
        boolean completed  = plugin.getDailyChallengeManager().isCompleted(uuid, type);
        int     progress   = plugin.getDailyChallengeManager().getProgress(uuid, type);
        int     target     = plugin.getDailyChallengeManager().getTarget(type);
        long    reward     = slotIndex < type.rewards.length ? type.rewards[slotIndex] : type.rewards[0];

        Material mat;
        if (completed) {
            mat = Material.LIME_CONCRETE;
        } else if (progress > 0) {
            mat = Material.YELLOW_CONCRETE;
        } else {
            mat = Material.RED_CONCRETE;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        meta.displayName(LEGACY.deserialize(type.displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize("§7" + type.getDescription(target)));
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        lore.add(LEGACY.deserialize("§7Fortschritt: " + bar(progress, target)));
        lore.add(LEGACY.deserialize("§7Belohnung: §e" + reward + " §7Münzen"));
        lore.add(LEGACY.deserialize("§8─────────────────────"));
        if (completed) {
            lore.add(LEGACY.deserialize("§a✔ Abgeschlossen!"));
        } else {
            lore.add(LEGACY.deserialize("§c✗ Noch nicht abgeschlossen"));
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
        if (!(event.getInventory().getHolder() instanceof DailyHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Close button
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // Challenge slots – read-only, just send info
        for (int i = 0; i < CHALLENGE_SLOTS.length; i++) {
            if (slot == CHALLENGE_SLOTS[i]) {
                List<ChallengeType> today = plugin.getDailyChallengeManager().getTodaysChallenges();
                if (i < today.size()) {
                    ChallengeType type      = today.get(i);
                    int           progress  = plugin.getDailyChallengeManager().getProgress(player.getUniqueId(), type);
                    int           target    = plugin.getDailyChallengeManager().getTarget(type);
                    boolean       completed = plugin.getDailyChallengeManager().isCompleted(player.getUniqueId(), type);

                    if (completed) {
                        player.sendMessage("§a✔ Diese Herausforderung hast du heute bereits abgeschlossen!");
                    } else {
                        player.sendMessage("§7Fortschritt: §f" + progress + "§8/§f" + target
                            + " §8– §7" + type.getDescription(target));
                    }
                }
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String bar(int current, int max) {
        int len    = 15;
        int filled = max > 0 ? Math.min(len, (int) Math.round(len * (double) current / max)) : 0;
        String col = filled >= len ? "§a" : filled > 0 ? "§e" : "§c";
        return col + "█".repeat(filled) + "§8" + "█".repeat(len - filled)
            + " §7(" + current + "/" + max + ")";
    }

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
