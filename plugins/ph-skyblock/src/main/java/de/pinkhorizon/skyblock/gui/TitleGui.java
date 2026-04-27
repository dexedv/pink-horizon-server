package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.enums.TitleType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Zeigt alle Titel: aktiver Titel hervorgehoben, kaufbare Titel mit Preis,
 * gesperrte mit Info wie freischalten.
 */
public class TitleGui extends GuiBase {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final PHSkyBlock plugin;
    private final Player player;
    private int page = 0;
    private final TitleType[] all = TitleType.values();

    public TitleGui(PHSkyBlock plugin, Player player) {
        super("<light_purple>✦ Titel-Auswahl", 6);
        this.plugin = plugin;
        this.player = player;
        build();
    }

    private void build() {
        inventory.clear();

        TitleType active = plugin.getTitleManager().getActiveTitle(player.getUniqueId());
        long coins = plugin.getCoinManager().getCoins(player.getUniqueId());

        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, all.length);

        for (int i = start; i < end; i++) {
            TitleType t = all[i];
            inventory.setItem(i - start, buildTitleItem(t, active, coins));
        }

        // Filler
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ");
        for (int s = end - start; s < inventory.getSize(); s++) {
            if (s < PAGE_SIZE || inventory.getItem(s) == null) {
                inventory.setItem(s, filler);
            }
        }
        for (int s = PAGE_SIZE; s < inventory.getSize(); s++) inventory.setItem(s, filler);

        // ── Navigation ─────────────────────────────────────────────────────────
        int totalPages = (int) Math.ceil((double) all.length / PAGE_SIZE);
        inventory.setItem(SLOT_INFO, item(
            Material.NAME_TAG,
            "<light_purple><bold>Titel auswählen",
            "<gray>Aktiver Titel: " + (active == TitleType.KEIN_TITEL
                ? "<gray>Kein Titel" : active.getCleanChatPrefix()),
            "<gray>Deine Coins: <gold>" + String.format("%,d", coins),
            "<gray>Seite: <white>" + (page + 1) + " / " + totalPages
        ));

        if (page > 0) {
            inventory.setItem(SLOT_PREV, item(Material.ARROW, "<gray>◀ Zurück"));
        }
        if (end < all.length) {
            inventory.setItem(SLOT_NEXT, item(Material.ARROW, "<gray>Weiter ▶"));
        }
    }

    private ItemStack buildTitleItem(TitleType t, TitleType active, long playerCoins) {
        boolean isActive  = t == active;
        boolean owned     = plugin.getAchievementManager().ownsTitle(player.getUniqueId(), t);
        boolean canBuy    = t.isBuyable() && !owned && playerCoins >= t.getBuyPrice();
        boolean tooExpensive = t.isBuyable() && !owned && playerCoins < t.getBuyPrice();

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Vorschau: " + t.getCleanChatPrefix() + "<white>Spielername");
        lore.add(" ");

        Material mat;
        String nameDisplay;

        if (isActive) {
            mat = Material.LIME_DYE;
            nameDisplay = "<green>★ " + t.getCleanDisplayName() + " <gray>[Aktiv]";
            lore.add("<green>✔ Aktuell aktiver Titel.");
        } else if (owned) {
            mat = Material.CYAN_DYE;
            nameDisplay = "<aqua>" + t.getCleanDisplayName();
            lore.add("<green>✔ Du besitzt diesen Titel.");
            lore.add("<yellow>Klicke zum Aktivieren.");
        } else if (t.isBuyable()) {
            mat = canBuy ? Material.YELLOW_DYE : Material.RED_DYE;
            nameDisplay = (tooExpensive ? "<red>" : "<yellow>") + t.getCleanDisplayName();
            lore.add("<gold>Preis: <yellow>" + String.format("%,d", t.getBuyPrice()) + " Coins");
            if (canBuy) {
                lore.add("<yellow>Klicke zum Kaufen.");
            } else {
                lore.add("<red>Nicht genug Coins! (Fehlen: <yellow>"
                    + String.format("%,d", t.getBuyPrice() - playerCoins) + "<red>)");
            }
        } else {
            mat = Material.GRAY_DYE;
            nameDisplay = "<dark_gray>✘ " + t.getCleanDisplayName();
            lore.add("<dark_gray>Freischaltbar via Achievement.");
        }

        if (t == TitleType.KEIN_TITEL) {
            mat = Material.BARRIER;
            nameDisplay = "<gray>Kein Titel";
            lore.clear();
            lore.add("<gray>Entfernt den aktiven Titel.");
            lore.add("<yellow>Klicke zum Aktivieren.");
        }

        return item(mat, nameDisplay, lore.toArray(new String[0]));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == SLOT_PREV && page > 0) { page--; build(); return; }
        if (slot == SLOT_NEXT && (page + 1) * PAGE_SIZE < all.length) { page++; build(); return; }
        if (slot == SLOT_INFO) return;

        int idx = page * PAGE_SIZE + slot;
        if (idx < 0 || idx >= all.length) return;
        TitleType t = all[idx];

        boolean owned = plugin.getAchievementManager().ownsTitle(player.getUniqueId(), t);

        if (owned || t == TitleType.KEIN_TITEL) {
            plugin.getTitleManager().setActiveTitle(player, t);
            build();
        } else if (t.isBuyable()) {
            boolean bought = plugin.getTitleManager().buyTitle(player, t);
            if (bought) build();
        }
    }
}
