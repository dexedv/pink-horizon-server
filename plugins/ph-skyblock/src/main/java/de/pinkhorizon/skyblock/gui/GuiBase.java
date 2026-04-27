package de.pinkhorizon.skyblock.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Abstrakte Basis für alle GUI-Klassen.
 * Implementiert InventoryHolder damit der GuiListener das GUI erkennt.
 */
public abstract class GuiBase implements InventoryHolder {

    protected static final MiniMessage MM = MiniMessage.miniMessage();

    protected final Inventory inventory;

    protected GuiBase(String title, int rows) {
        this.inventory = Bukkit.createInventory(this, Math.min(6, Math.max(1, rows)) * 9,
            MM.deserialize(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Öffnet das GUI für den Spieler. */
    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Wird aufgerufen wenn der Spieler auf ein Item klickt. */
    public abstract void handleClick(InventoryClickEvent event);

    // ── Item-Builder Helfer ───────────────────────────────────────────────────

    protected ItemStack item(Material mat, String name, String... loreParts) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.displayName(MM.deserialize(name).decoration(
            net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        if (loreParts.length > 0) {
            List<Component> lore = Arrays.stream(loreParts)
                .map(l -> (Component) MM.deserialize(l).decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                .toList();
            meta.lore(lore);
        }
        it.setItemMeta(meta);
        return it;
    }

    protected ItemStack item(Material mat, int amount, String name, String... loreParts) {
        ItemStack it = item(mat, name, loreParts);
        it.setAmount(amount);
        return it;
    }

    /** Füllt alle leeren Slots mit einem grauen Glas-Pane (Dekoration). */
    protected void fillEmpty() {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler);
        }
    }

    /** Füllt einen bestimmten Bereich mit dem Filler-Item. */
    protected void fillRow(int row) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ");
        for (int col = 0; col < 9; col++) {
            int slot = row * 9 + col;
            if (slot < inventory.getSize()) inventory.setItem(slot, filler);
        }
    }

    /** Setzt einen farbigen Rahmen aus Glasscheiben. */
    protected void setBorder(Material glass) {
        ItemStack border = item(glass, "<dark_gray> ");
        int rows = inventory.getSize() / 9;
        for (int col = 0; col < 9; col++) {
            inventory.setItem(col, border);
            inventory.setItem((rows - 1) * 9 + col, border);
        }
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * 9, border);
            inventory.setItem(row * 9 + 8, border);
        }
    }

    protected ItemStack closeButton() {
        return item(Material.BARRIER, "<red><bold>Schließen", "<gray>Klicke zum Schließen.");
    }
}
