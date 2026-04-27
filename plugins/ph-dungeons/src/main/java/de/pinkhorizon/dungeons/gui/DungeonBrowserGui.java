package de.pinkhorizon.dungeons.gui;

import de.pinkhorizon.dungeons.DungeonType;
import de.pinkhorizon.dungeons.PHDungeons;
import de.pinkhorizon.dungeons.managers.DungeonManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * GUI: Dungeon-Browser – zeigt alle 5 Dungeons mit Tier, Spieleranzahl, Zeit und Rezept.
 * 3 Reihen.
 */
public class DungeonBrowserGui extends GuiBase {

    // 5 Dungeons in der Mitte-Reihe
    private static final int[] DUNGEON_SLOTS = {11, 12, 13, 14, 15};
    private static final DungeonType[] TYPES = DungeonType.values();

    private static final int SLOT_INFO  = 4;
    private static final int SLOT_CLOSE = 22;

    private final PHDungeons plugin;
    private final Player player;
    private final DungeonManager manager;

    public DungeonBrowserGui(PHDungeons plugin, Player player) {
        super("<dark_red>⚔ <bold>Dungeons</bold>", 3);
        this.plugin  = plugin;
        this.player  = player;
        this.manager = plugin.getDungeonManager();
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.RED_STAINED_GLASS_PANE);

        boolean inDungeon = manager.isInDungeon(player);

        inventory.setItem(SLOT_INFO, item(Material.MAP,
            "<red><bold>Dungeon-Browser",
            "<gray>Wähle einen Dungeon und betritt ihn.",
            inDungeon ? "<yellow>Du bist bereits in einem Dungeon!" : "",
            "",
            "<dark_gray>Dungeon-Karten selbst herstellen"
        ));

        for (int i = 0; i < DUNGEON_SLOTS.length && i < TYPES.length; i++) {
            DungeonType type = TYPES[i];
            String tierColor = switch (type.tier) {
                case 1 -> "<gray>";
                case 2 -> "<aqua>";
                case 3 -> "<green>";
                case 4 -> "<light_purple>";
                case 5 -> "<gold>";
                default -> "<white>";
            };

            String recipe = String.join(", ", type.cardRecipe);
            String time = (type.timeLimitSeconds / 60) + " min";

            inventory.setItem(DUNGEON_SLOTS[i], item(type.guiIcon,
                tierColor + "<bold>" + type.displayName,
                "<dark_gray>Tier " + type.tier
                    + " <dark_gray>│ <white>" + type.minPlayers + "-" + type.maxPlayers + " Spieler",
                "<gray>" + type.description,
                "",
                "<gray>Zeitlimit: <white>" + time,
                "<gray>Karte benötigt: <dark_gray>" + recipe,
                "",
                inDungeon ? "<red>Verlasse deinen Dungeon zuerst!"
                          : "<yellow>► Klicken zum Betreten"
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

        for (int i = 0; i < DUNGEON_SLOTS.length && i < TYPES.length; i++) {
            if (slot == DUNGEON_SLOTS[i]) {
                p.closeInventory();
                manager.startDungeon(p, TYPES[i]);
                return;
            }
        }
    }
}
