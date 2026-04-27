package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.enums.AchievementType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Zeigt alle Achievements mit Fortschritts-Indikator.
 * Grün = freigeschaltet, Grau = noch nicht.
 * Paginierung: je 45 Achievements pro Seite.
 */
public class AchievementGui extends GuiBase {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final PHSkyBlock plugin;
    private final Player player;
    private int page;
    private final AchievementType[] all = AchievementType.values();

    public AchievementGui(PHSkyBlock plugin, Player player) {
        super("<gold>★ Achievements", 6);
        this.plugin = plugin;
        this.player = player;
        this.page = 0;
        build();
    }

    private void build() {
        inventory.clear();

        Set<String> unlocked = plugin.getAchievementManager().getUnlocked(player.getUniqueId());
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, all.length);

        for (int i = start; i < end; i++) {
            AchievementType a = all[i];
            boolean done = unlocked.contains(a.getId());
            inventory.setItem(i - start, buildAchievementItem(a, done));
        }

        // Filler für leere Slots in Zeile 5+6
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ");
        for (int s = end - start; s < PAGE_SIZE; s++) inventory.setItem(s, filler);
        for (int s = PAGE_SIZE; s < inventory.getSize(); s++) inventory.setItem(s, filler);

        // ── Navigation ─────────────────────────────────────────────────────────
        long unlockedCount = unlocked.stream()
            .filter(id -> !id.startsWith("title_"))
            .count();
        int totalPages = (int) Math.ceil((double) all.length / PAGE_SIZE);

        inventory.setItem(SLOT_INFO, item(
            Material.NETHER_STAR,
            "<gold><bold>Achievements",
            "<gray>Freigeschaltet: <yellow>" + unlockedCount + " / " + all.length,
            "<gray>Seite: <white>" + (page + 1) + " / " + totalPages
        ));

        if (page > 0) {
            inventory.setItem(SLOT_PREV, item(
                Material.ARROW, "<gray>◀ Vorherige Seite", "<gray>Seite " + page));
        }
        if (end < all.length) {
            inventory.setItem(SLOT_NEXT, item(
                Material.ARROW, "<gray>Nächste Seite ▶", "<gray>Seite " + (page + 2)));
        }
    }

    private ItemStack buildAchievementItem(AchievementType a, boolean done) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + a.getDescription());
        lore.add(" ");
        if (a.getCoinReward() > 0) {
            lore.add("<gold>Belohnung: <yellow>" + String.format("%,d", a.getCoinReward()) + " Coins");
        }
        if (a.getTitleReward() != null) {
            lore.add("<light_purple>Titel: " + a.getTitleReward().getCleanChatPrefix());
        }
        lore.add(" ");
        lore.add(done ? "<green>✔ Freigeschaltet!" : "<gray>Noch nicht freigeschaltet.");

        Material mat = done
            ? a.getIcon()
            : Material.GRAY_DYE;

        String namePrefix = done ? "<green>✔ " : "<dark_gray>✘ ";
        return item(mat, namePrefix + a.getName(), lore.toArray(new String[0]));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == SLOT_PREV && page > 0) {
            page--;
            build();
        } else if (slot == SLOT_NEXT
                   && (page + 1) * PAGE_SIZE < all.length) {
            page++;
            build();
        } else if (slot == SLOT_INFO) {
            // nichts tun
        }
        // Klick auf Achievements: kein Effekt (nur Anzeige)
    }
}
