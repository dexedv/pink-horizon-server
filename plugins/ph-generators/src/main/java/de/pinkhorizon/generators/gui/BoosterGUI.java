package de.pinkhorizon.generators.gui;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.data.StoredBooster;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * GUI zum Anzeigen und Aktivieren gespeicherter Booster.
 * Geöffnet mit /gen booster
 */
public class BoosterGUI implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String GUI_PLAIN_TITLE = "⚡ Deine Booster";

    public BoosterGUI(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        Inventory inv = Bukkit.createInventory(null, 27,
                MM.deserialize("<gold><bold>⚡ Deine Booster</bold></gold>"));

        List<StoredBooster> boosters = data.getStoredBoosters();

        if (boosters.isEmpty()) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta m = empty.getItemMeta();
            m.displayName(MM.deserialize("<gray>Keine Booster vorhanden"));
            m.lore(List.of(
                    MM.deserialize("<dark_gray>Booster kannst du im Shop kaufen!"),
                    MM.deserialize("<dark_gray>play.pinkhorizon.de")
            ));
            empty.setItemMeta(m);
            inv.setItem(13, empty);
        } else {
            boolean hasActive = data.hasActiveBooster();
            for (int i = 0; i < Math.min(boosters.size(), 27); i++) {
                StoredBooster b = boosters.get(i);
                ItemStack item = new ItemStack(Material.BLAZE_POWDER);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(MM.deserialize("<gold>⚡ Booster <yellow>x" + b.multiplier()));
                if (hasActive) {
                    long rem = data.getBoosterExpiry() - System.currentTimeMillis() / 1000;
                    meta.lore(List.of(
                            MM.deserialize("<gray>Multiplikator: <gold>x" + b.multiplier()),
                            MM.deserialize("<gray>Dauer: <yellow>" + b.durationMinutes() + " Minuten"),
                            Component.empty(),
                            MM.deserialize("<red>⚠ Aktiver Booster wird überschrieben!"),
                            MM.deserialize("<dark_gray>Verbleibend: " + (rem / 60) + "m " + (rem % 60) + "s"),
                            Component.empty(),
                            MM.deserialize("<green>Klicken zum Aktivieren!")
                    ));
                } else {
                    meta.lore(List.of(
                            MM.deserialize("<gray>Multiplikator: <gold>x" + b.multiplier()),
                            MM.deserialize("<gray>Dauer: <yellow>" + b.durationMinutes() + " Minuten"),
                            Component.empty(),
                            MM.deserialize("<green>Klicken zum Aktivieren!")
                    ));
                }
                item.setItemMeta(meta);
                inv.setItem(i, item);
            }
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String plainTitle = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!plainTitle.equals(GUI_PLAIN_TITLE)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() != Material.BLAZE_POWDER) return;

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        int slot = event.getSlot();
        if (slot < 0 || slot >= data.getStoredBoosters().size()) return;

        StoredBooster booster = data.getStoredBoosters().get(slot);
        data.removeStoredBooster(slot);
        data.activateBooster(booster.multiplier(), booster.durationMinutes() * 60);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getRepository().savePlayer(data));

        player.closeInventory();
        player.sendMessage(MM.deserialize(
                "<green>✦ Booster aktiviert! <gold>x" + booster.multiplier()
                + " <gray>für <yellow>" + booster.durationMinutes() + " Minuten!"));
    }
}
