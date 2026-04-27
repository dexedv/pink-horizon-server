package de.pinkhorizon.companions.gui;

import de.pinkhorizon.companions.CompanionType;
import de.pinkhorizon.companions.PHCompanions;
import de.pinkhorizon.companions.managers.CompanionManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * GUI: Begleiter-Übersicht – zeigt alle 6 Begleiter, Status und erlaubt Beschwören/Entlassen.
 * 4 Reihen.
 */
public class CompanionMenuGui extends GuiBase {

    // Companion-Slots (6 Begleiter)
    private static final int[] COMPANION_SLOTS = {10, 12, 14, 16, 28, 30};
    private static final CompanionType[] TYPES = CompanionType.values();

    private static final int SLOT_INFO  = 4;
    private static final int SLOT_CLOSE = 31;

    private final PHCompanions plugin;
    private final Player player;
    private final CompanionManager manager;

    public CompanionMenuGui(PHCompanions plugin, Player player) {
        super("<gold>🐾 <bold>Begleiter</bold>", 4);
        this.plugin  = plugin;
        this.player  = player;
        this.manager = plugin.getCompanionManager();
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.ORANGE_STAINED_GLASS_PANE);

        inventory.setItem(SLOT_INFO, item(Material.BOOK,
            "<gold><bold>Begleiter-System",
            "<gray>Aktiv: max. <white>2 gleichzeitig",
            "",
            "<dark_gray>Klick  = Beschwören / Entlassen",
            "<dark_gray>Shift+Klick = Füttern"
        ));

        for (int i = 0; i < COMPANION_SLOTS.length && i < TYPES.length; i++) {
            CompanionType type = TYPES[i];
            boolean owns   = manager.ownsCompanion(player.getUniqueId(), type);
            boolean active = manager.isActive(player.getUniqueId(), type);
            int hungerPct  = manager.getHungerPercent(player.getUniqueId(), type);

            if (!owns) {
                inventory.setItem(COMPANION_SLOTS[i], item(Material.GRAY_STAINED_GLASS_PANE,
                    "<dark_gray>" + type.displayName,
                    "<red>Nicht freigeschaltet",
                    "<gray>Erhältlich beim Spawn-NPC oder im AH"
                ));
            } else {
                Material icon = type.entityType.getEntityClass() != null
                    ? getIconFor(type) : Material.SLIME_BALL;
                String statusColor = active ? "<green>" : "<dark_gray>";
                String hungerColor = hungerPct > 50 ? "<green>" : hungerPct > 20 ? "<yellow>" : "<red>";

                inventory.setItem(COMPANION_SLOTS[i], item(icon,
                    statusColor + "<bold>" + type.displayName,
                    "<gray>Fähigkeit: <white>" + type.ability,
                    "",
                    "<gray>Status: " + (active ? "<green>Aktiv" : "<dark_gray>Inaktiv"),
                    "<gray>Hunger: " + hungerColor + hungerPct + "%"
                        + (hungerPct == 0 ? " <red>(schläft!)" : ""),
                    "",
                    active ? "<red>► Klicken zum Entlassen" : "<green>► Klicken zum Beschwören",
                    "<yellow>► Shift+Klick zum Füttern"
                ));
            }
        }

        inventory.setItem(SLOT_CLOSE, closeButton());
        fillEmpty();
    }

    private Material getIconFor(CompanionType type) {
        return switch (type) {
            case GOLEM_GUSTAV  -> Material.IRON_BLOCK;
            case HEX_HELGA     -> Material.BREWING_STAND;
            case FELD_FELIX    -> Material.WHEAT;
            case BERG_BERT     -> Material.IRON_PICKAXE;
            case HANDEL_HANNA  -> Material.EMERALD;
            case VOID_VIKTOR   -> Material.ENDER_EYE;
        };
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) { p.closeInventory(); return; }

        for (int i = 0; i < COMPANION_SLOTS.length && i < TYPES.length; i++) {
            if (slot == COMPANION_SLOTS[i]) {
                CompanionType type = TYPES[i];
                if (!manager.ownsCompanion(p.getUniqueId(), type)) return;

                if (event.isShiftClick()) {
                    manager.feedCompanion(p, type);
                } else if (manager.isActive(p.getUniqueId(), type)) {
                    manager.dismissCompanion(p, type);
                } else {
                    manager.summonCompanion(p, type);
                }
                p.closeInventory();
                return;
            }
        }
    }
}
