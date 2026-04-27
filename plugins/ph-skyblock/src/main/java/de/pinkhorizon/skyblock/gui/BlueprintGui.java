package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.managers.BlueprintManager;
import de.pinkhorizon.skyblock.managers.BlueprintManager.BlueprintMeta;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI: Blueprint-Liste – zeigt gespeicherte Blueprints, erlaubt Laden.
 * 4 Reihen.
 */
public class BlueprintGui extends GuiBase {

    // Item-Slots 1–3 (Seiten à 21 Blueprints)
    private static final int[] BP_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25
    };
    private static final int SLOT_PREV  = 27;
    private static final int SLOT_NEXT  = 35;
    private static final int SLOT_CLOSE = 31;

    private final PHSkyBlock plugin;
    private final Player player;
    private final List<BlueprintMeta> blueprints;
    private int page = 0;

    public BlueprintGui(PHSkyBlock plugin, Player player, List<BlueprintMeta> blueprints) {
        super("<dark_green>📐 <bold>Blueprints</bold>", 4);
        this.plugin = plugin;
        this.player = player;
        this.blueprints = new ArrayList<>(blueprints);
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.GREEN_STAINED_GLASS_PANE);

        // ── Info ──────────────────────────────────────────────────────────────
        inventory.setItem(4, item(Material.MAP,
            "<green><bold>Insel-Blueprints",
            "<gray>Gesamt: <yellow>" + blueprints.size(),
            "",
            "<dark_gray>Klick = Blueprint laden",
            "<dark_gray>Shift-Klick = Blueprint teilen"
        ));

        int start = page * BP_SLOTS.length;
        for (int i = 0; i < BP_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= blueprints.size()) break;
            BlueprintMeta bp = blueprints.get(idx);
            String shared  = bp.shared()   ? "<aqua>✔ Geteilt"  : "<gray>Privat";
            String approved = bp.approved() ? "<green>✔ Genehmigt" : "<yellow>⏳ Ausstehend";
            inventory.setItem(BP_SLOTS[i], item(Material.FILLED_MAP,
                "<green><bold>" + bp.name(),
                "<gray>" + (bp.description().isBlank() ? "Kein Beschreibung." : bp.description()),
                "",
                shared,
                bp.shared() ? approved : "",
                "",
                "<yellow>► Klicken zum Laden",
                "<aqua>► Shift+Klick zum Teilen"
            ));
        }

        // ── Navigation ────────────────────────────────────────────────────────
        if (page > 0)
            inventory.setItem(SLOT_PREV, item(Material.ARROW, "<white>◄ Zurück"));
        if ((page + 1) * BP_SLOTS.length < blueprints.size())
            inventory.setItem(SLOT_NEXT, item(Material.ARROW, "<white>Weiter ►"));

        if (blueprints.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER,
                "<gray>Keine Blueprints gespeichert",
                "<dark_gray>Nutze /blueprint save <name>"
            ));
        }

        inventory.setItem(SLOT_CLOSE, closeButton());
        fillEmpty();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) { p.closeInventory(); return; }
        if (slot == SLOT_PREV && page > 0) { page--; build(); return; }
        if (slot == SLOT_NEXT) { page++; build(); return; }

        for (int i = 0; i < BP_SLOTS.length; i++) {
            if (slot == BP_SLOTS[i]) {
                int idx = page * BP_SLOTS.length + i;
                if (idx >= blueprints.size()) return;
                BlueprintMeta bp = blueprints.get(idx);
                if (event.isShiftClick()) {
                    p.closeInventory();
                    plugin.getBlueprintManager().shareBlueprint(p, bp.name());
                } else {
                    p.closeInventory();
                    plugin.getBlueprintManager().loadBlueprint(p, bp.name());
                }
                return;
            }
        }
    }
}
