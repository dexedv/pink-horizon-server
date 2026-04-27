package de.pinkhorizon.companions.gui;

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

public abstract class GuiBase implements InventoryHolder {

    protected static final MiniMessage MM = MiniMessage.miniMessage();
    protected final Inventory inventory;

    protected GuiBase(String title, int rows) {
        this.inventory = Bukkit.createInventory(this, Math.min(6, Math.max(1, rows)) * 9,
            MM.deserialize(title));
    }

    @Override public Inventory getInventory() { return inventory; }

    public void open(Player player) { player.openInventory(inventory); }

    public abstract void handleClick(InventoryClickEvent event);

    protected ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.displayName(MM.deserialize(name).decoration(
            net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = Arrays.stream(lore)
                .map(s -> (Component) MM.deserialize(s).decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                .toList();
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    protected void fillEmpty() {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler);
        }
    }

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
