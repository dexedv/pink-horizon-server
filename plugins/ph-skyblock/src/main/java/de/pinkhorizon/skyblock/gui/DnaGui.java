package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.IslandDna;
import de.pinkhorizon.skyblock.enums.IslandGene;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import de.pinkhorizon.skyblock.managers.IslandDnaManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * GUI: Insel-DNA – zeigt Gene der eigenen Insel + Kombinationsinfo.
 * 4 Reihen, Rand aus magenta Glas.
 */
public class DnaGui extends GuiBase {

    private static final int[] GENE_SLOTS = {10, 12, 14, 16, 22};
    private static final int SLOT_COMBINE = 31;
    private static final int SLOT_CLOSE   = 35;

    private static final Material[] GENE_MATS = {
        Material.MAGENTA_DYE, Material.PINK_DYE, Material.PURPLE_DYE,
        Material.CYAN_DYE, Material.LIME_DYE
    };

    private final PHSkyBlock plugin;
    private final Player player;

    public DnaGui(PHSkyBlock plugin, Player player) {
        super("<dark_gray>[<light_purple><bold>Insel-DNA</bold></light_purple><dark_gray>]", 4);
        this.plugin = plugin;
        this.player = player;
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.MAGENTA_STAINED_GLASS_PANE);

        var islandOpt = BentoBoxHook.getIsland(player.getUniqueId());
        if (islandOpt.isEmpty()) {
            inventory.setItem(13, item(Material.BARRIER, "<red>Keine Insel",
                "<gray>Du brauchst eine SkyBlock-Insel."));
            inventory.setItem(SLOT_CLOSE, closeButton());
            fillEmpty();
            return;
        }

        UUID islandUuid = UUID.fromString(islandOpt.get().getUniqueId());
        IslandDnaManager dnaManager = plugin.getIslandDnaManager();
        IslandDna dna = dnaManager.getDna(islandUuid);

        // ── Titel-Item ────────────────────────────────────────────────────────
        inventory.setItem(4, item(Material.DRAGON_EGG,
            "<light_purple><bold>Insel-DNA",
            "<gray>Jede Insel hat einzigartige Gene.",
            "<gray>Kombinationen: <yellow>" + dna.combinationsUsed() + "<gray>/3",
            "",
            "<dark_gray>Verbinde 2 Inseln für eine Kind-DNA"
        ));

        // ── Gene anzeigen ─────────────────────────────────────────────────────
        var genes = dna.genes();
        for (int i = 0; i < Math.min(genes.size(), GENE_SLOTS.length); i++) {
            IslandGene g = genes.get(i);
            Material mat = GENE_MATS[i % GENE_MATS.length];
            inventory.setItem(GENE_SLOTS[i], item(mat,
                "<gold><bold>" + g.displayName,
                "<gray>" + g.description,
                "",
                "<dark_gray>Gen " + (i + 1) + " / " + genes.size()
            ));
        }

        // ── Kombinieren-Button ────────────────────────────────────────────────
        boolean canCombine = dna.combinationsUsed() < 3;
        inventory.setItem(SLOT_COMBINE, item(
            canCombine ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
            canCombine ? "<green><bold>DNA kombinieren" : "<red>Nicht mehr kombinierbar",
            "<gray>Kosten: <gold>1.000.000 Coins",
            "<gray>Kombinationen verbraucht: <yellow>" + dna.combinationsUsed() + "<gray>/3",
            canCombine ? "<green>Klicke um zu kombinieren" : "<red>Limit erreicht"
        ));

        inventory.setItem(SLOT_CLOSE, closeButton());
        fillEmpty();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) {
            p.closeInventory();
        } else if (slot == SLOT_COMBINE) {
            p.closeInventory();
            p.sendMessage(MM.deserialize("<gray>Nutze <yellow>/skydna combine <spielername> <gray>um eine DNA-Kombination zu starten."));
        }
    }
}
