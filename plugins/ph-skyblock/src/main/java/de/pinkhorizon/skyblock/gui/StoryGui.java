package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.managers.StoryManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * GUI: Story-Fortschritt & Nyx-Status.
 * 4 Reihen.
 */
public class StoryGui extends GuiBase {

    private static final int[] CHAPTER_SLOTS = {10, 12, 14, 16, 22};
    private static final int SLOT_NYX   = 31;
    private static final int SLOT_CLOSE = 35;

    private static final String[] CHAPTER_NAMES = {
        "Kapitel 1: Der Alte Seher",
        "Kapitel 2: Das erste Ritual",
        "Kapitel 3: Der Drachenpakt",
        "Kapitel 4: Nyxs Botschaft",
        "Kapitel 5: Nyx erwacht"
    };
    private static final String[] CHAPTER_REQ = {
        "Starte auf deiner Insel.",
        "Insel-Level 50 erreichen.",
        "Insel-Level 200 erreichen.",
        "Prestige 1 erreichen.",
        "Community: Void-Erwachen aktivieren."
    };
    private static final Material[] CHAPTER_MATS = {
        Material.BOOK, Material.WRITTEN_BOOK, Material.BOOKSHELF,
        Material.ENCHANTED_BOOK, Material.DRAGON_HEAD
    };

    private final PHSkyBlock plugin;
    private final Player player;

    public StoryGui(PHSkyBlock plugin, Player player) {
        super("<dark_purple>📖 <bold>Story & Nyx</bold>", 4);
        this.plugin = plugin;
        this.player = player;
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.PURPLE_STAINED_GLASS_PANE);

        StoryManager sm = plugin.getStoryManager();
        int currentChapter = sm.getChapter(player.getUniqueId());
        int nyxProgress    = sm.getNyxProgress();
        boolean nyxActive  = sm.isNyxActive();

        inventory.setItem(4, item(Material.DRAGON_EGG,
            "<light_purple><bold>Die Legende von Nyx",
            "<gray>Die Void unter den Inseln ist lebendig.",
            "<gray>Nyx schläft und träumt die Welt.",
            "",
            "<gray>Dein Kapitel: <yellow>" + currentChapter + " / 5"
        ));

        for (int i = 0; i < 5; i++) {
            int chap = i + 1;
            boolean unlocked = currentChapter >= chap;
            boolean current  = currentChapter == chap;
            Material mat = unlocked ? CHAPTER_MATS[i] : Material.GRAY_DYE;
            String color = unlocked ? (current ? "<yellow>" : "<green>") : "<dark_gray>";
            String status = current ? " <yellow>(Aktuell)" : (unlocked ? " <green>✔" : " <dark_gray>✗");

            inventory.setItem(CHAPTER_SLOTS[i], item(mat,
                color + "<bold>" + CHAPTER_NAMES[i] + status,
                unlocked ? "<gray>" + CHAPTER_REQ[i] : "<dark_gray>Noch nicht freigeschaltet.",
                "",
                unlocked ? "<green>Abgeschlossen" : "<gray>Anforderung: <white>" + CHAPTER_REQ[i]
            ));
        }

        // ── Nyx-Fortschritt ───────────────────────────────────────────────────
        String nyxStatus = nyxActive ? "<light_purple>🔥 EVENT AKTIV!" : "<gray>Fortschritt: <yellow>" + nyxProgress + "%";
        inventory.setItem(SLOT_NYX, item(
            nyxActive ? Material.END_PORTAL_FRAME : Material.ENDER_EYE,
            "<light_purple><bold>Nyx-Erwachen",
            nyxStatus,
            "<dark_gray>Schließe genug Community-Rituale ab",
            "<dark_gray>um Nyx zu erwecken.",
            "",
            nyxActive ? "<light_purple>✦ Boss-Event aktiv!" : "<gray>" + nyxProgress + "% – " + (100 - nyxProgress) + "% fehlt"
        ));

        inventory.setItem(SLOT_CLOSE, closeButton());
        fillEmpty();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getSlot() == SLOT_CLOSE) p.closeInventory();
    }
}
