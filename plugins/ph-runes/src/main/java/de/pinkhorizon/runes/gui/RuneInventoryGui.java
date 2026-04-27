package de.pinkhorizon.runes.gui;

import de.pinkhorizon.runes.PHRunes;
import de.pinkhorizon.runes.RuneType;
import de.pinkhorizon.runes.managers.RuneManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI: Runen-Inventar – zeigt alle Runen die der Spieler besitzt.
 * Klick auf eine Rune = auf Item in Haupthand gravieren.
 * 5 Reihen (3 Reihen Runen + Deko).
 */
public class RuneInventoryGui extends GuiBase {

    private static final int[] RUNE_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34
    };

    private static final int SLOT_INFO  = 4;
    private static final int SLOT_CLOSE = 40;

    private final PHRunes plugin;
    private final Player player;
    private final RuneManager manager;
    private final List<RuneType> displayedRunes = new ArrayList<>();

    public RuneInventoryGui(PHRunes plugin, Player player) {
        super("<dark_purple>✦ <bold>Runen-Inventar</bold>", 5);
        this.plugin  = plugin;
        this.player  = player;
        this.manager = plugin.getRuneManager();
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.PURPLE_STAINED_GLASS_PANE);
        displayedRunes.clear();

        inventory.setItem(SLOT_INFO, item(Material.ENCHANTED_BOOK,
            "<light_purple><bold>Runen-Inventar",
            "<gray>Klicke eine Rune um sie auf dein",
            "<gray>Item in der <white>Haupthand <gray>zu gravieren.",
            "",
            "<dark_gray>Runen-Quellen: Void-Fishing, Dungeons, Rituale"
        ));

        Map<RuneType, Integer> runes = manager.getPlayerRunes(player.getUniqueId());

        if (runes.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER,
                "<red>Keine Runen",
                "<gray>Fange Runen durch Void-Fishing,",
                "<gray>Dungeons oder Ritual-Belohnungen."
            ));
        } else {
            int slotIdx = 0;
            for (Map.Entry<RuneType, Integer> entry : runes.entrySet()) {
                if (slotIdx >= RUNE_SLOTS.length) break;
                RuneType rune = entry.getKey();
                int amount = entry.getValue();
                displayedRunes.add(rune);

                ItemStack runeItem = manager.createRuneItem(rune);
                runeItem.setAmount(Math.min(64, amount));

                // Lore ergänzen mit Hinweis
                var meta = runeItem.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(
                    meta.lore() != null ? meta.lore() : List.of());
                lore.add(MM.deserialize(""));
                lore.add(MM.deserialize("<yellow>► Klicken = auf Haupthand-Item gravieren").decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(MM.deserialize("<dark_gray>Bestand: <white>" + amount + "x").decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(lore);
                runeItem.setItemMeta(meta);

                inventory.setItem(RUNE_SLOTS[slotIdx], runeItem);
                slotIdx++;
            }
        }

        inventory.setItem(SLOT_CLOSE, closeButton());
        fillEmpty();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) { p.closeInventory(); return; }

        for (int i = 0; i < RUNE_SLOTS.length; i++) {
            if (slot == RUNE_SLOTS[i] && i < displayedRunes.size()) {
                RuneType rune = displayedRunes.get(i);
                p.closeInventory();
                manager.engraveRune(p, rune);
                return;
            }
        }
    }
}
