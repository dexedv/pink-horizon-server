package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import de.pinkhorizon.skyblock.managers.RitualManager;
import de.pinkhorizon.skyblock.managers.RitualManager.RitualType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * GUI: Ritual-Übersicht – alle 8 Rituale mit Cooldown-Status.
 * 6 Reihen. Klick auf Ritual = aktivieren.
 */
public class RitualGui extends GuiBase {

    // Ritual-Slots (Mitte des 6×9-Grids)
    private static final int[] RITUAL_SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};
    private static final int SLOT_CLOSE = 49;

    private static final Material[] RITUAL_MATS = {
        Material.GOLD_BLOCK,       // Sonnensegen
        Material.OBSIDIAN,          // Abgrundöffnung
        Material.DRAGON_EGG,        // Drachenpakt
        Material.NETHERITE_BLOCK,   // Steingeist
        Material.HAY_BLOCK,         // Ernte-Tanz
        Material.END_CRYSTAL,       // Sterne-Rufen
        Material.EMERALD_BLOCK,     // Händler-Geist
        Material.CRYING_OBSIDIAN    // Void-Erwachen
    };

    private final PHSkyBlock plugin;
    private final Player player;
    private UUID islandUuid;

    public RitualGui(PHSkyBlock plugin, Player player) {
        super("<dark_purple>✦ <bold>Rituale</bold> ✦", 6);
        this.plugin = plugin;
        this.player = player;
        var islandOpt = BentoBoxHook.getIsland(player.getUniqueId());
        this.islandUuid = islandOpt.map(i -> i.getUniqueId()).orElse(null);
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.PURPLE_STAINED_GLASS_PANE);

        // ── Info-Item ─────────────────────────────────────────────────────────
        inventory.setItem(4, item(Material.BEACON,
            "<light_purple><bold>Insel-Rituale",
            "<gray>Baue die Block-Muster auf deiner Insel",
            "<gray>und klicke hier um ein Ritual zu aktivieren.",
            "",
            "<dark_gray>Grün = Bereit | Rot = Cooldown"
        ));

        RitualManager rm = plugin.getRitualManager();
        RitualType[] types = RitualType.values();

        for (int i = 0; i < Math.min(types.length, RITUAL_SLOTS.length); i++) {
            RitualType r = types[i];
            boolean ready = rm.isReady(islandUuid, r);
            long secLeft = rm.secondsUntilReady(islandUuid, r);
            String cdStr = ready ? "<green>✔ Bereit" : "<red>✘ Cooldown: " + formatTime(secLeft);

            Material mat = RITUAL_MATS[i % RITUAL_MATS.length];
            inventory.setItem(RITUAL_SLOTS[i], item(mat,
                (ready ? "<green>" : "<red>") + "<bold>" + r.displayName,
                "<gray>" + r.requirement,
                "",
                cdStr,
                "",
                "<dark_gray>Cooldown: " + formatTime(r.cooldownSeconds),
                ready ? "<yellow>► Klicken zum Aktivieren" : "<dark_gray>Bitte warten…"
            ));
        }

        inventory.setItem(SLOT_CLOSE, closeButton());
        fillEmpty();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) {
            p.closeInventory();
            return;
        }

        for (int i = 0; i < RITUAL_SLOTS.length; i++) {
            if (slot == RITUAL_SLOTS[i]) {
                RitualType[] types = RitualType.values();
                if (i < types.length) {
                    p.closeInventory();
                    plugin.getRitualManager().activateRitual(p, types[i]);
                }
                return;
            }
        }
    }

    private static String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
