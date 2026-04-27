package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Generator;
import de.pinkhorizon.skyblock.enums.GeneratorTier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * GUI zum Verwalten eines einzelnen Generators.
 * Zeigt Puffer-Inhalt, Level, Upgrade-Kosten und Auto-Sell-Toggle.
 */
public class GeneratorGui extends GuiBase {

    private final PHSkyBlock plugin;
    private final Player player;
    private Generator gen;

    // Feste Slot-Positionen
    private static final int SLOT_INFO      = 4;   // Mitte oben: Generator-Info
    private static final int SLOT_COLLECT   = 39;  // Gelb: alles einsammeln
    private static final int SLOT_UPGRADE   = 41;  // Grün/Rot: upgraden
    private static final int SLOT_AUTOSELL  = 49;  // Toggle Auto-Sell
    private static final int SLOT_CLOSE     = 53;  // Schließen

    public GeneratorGui(PHSkyBlock plugin, Player player, Generator gen) {
        super("<gold>⚙ Generator <gray>– <white>Level " + gen.getLevel(), 6);
        this.plugin = plugin;
        this.player = player;
        this.gen = gen;
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.BLACK_STAINED_GLASS_PANE);

        // ── Info-Item (Generator-Ikone) ────────────────────────────────────────
        long cost       = gen.getUpgradeCost();
        long coins      = plugin.getCoinManager().getCoins(player.getUniqueId());
        String costFmt  = String.format("%,d", cost);
        String coinsFmt = String.format("%,d", coins);

        inventory.setItem(SLOT_INFO, item(
            GeneratorTier.visualMaterial(gen.getLevel()),
            "<gold><bold>⚙ Generator</bold>",
            "<gray>Level: <yellow>" + gen.getLevel() + " / " + Generator.MAX_LEVEL,
            "<gray>Produziert: <white>" + String.format("%,d", gen.getTotalProduced()) + " Items",
            "<gray>Puffer: <white>" + gen.getBuffer().size() + " / " + Generator.MAX_BUFFER,
            " ",
            "<gray>Intervall: <white>" + formatInterval(GeneratorTier.tickInterval(gen.getLevel()))
        ));

        // ── Puffer-Vorschau (Slots 10-16, 19-25) ─────────────────────────────
        List<ItemStack> buf = gen.getBuffer();
        int[] previewSlots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25};
        for (int i = 0; i < previewSlots.length; i++) {
            if (i < buf.size()) {
                inventory.setItem(previewSlots[i], buf.get(i).clone());
            } else {
                inventory.setItem(previewSlots[i],
                    item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<dark_gray>Leer"));
            }
        }

        // ── Alle einsammeln ───────────────────────────────────────────────────
        Material collectMat = gen.getBuffer().isEmpty()
            ? Material.GRAY_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
        inventory.setItem(SLOT_COLLECT, item(
            collectMat, "<yellow><bold>↓ Alle einsammeln",
            "<gray>Nimmt alle " + gen.getBuffer().size() + " Items aus dem Puffer.",
            gen.getBuffer().isEmpty() ? "<red>Der Puffer ist leer." : "<green>Klicke zum Einsammeln."
        ));

        // ── Upgrade-Button ────────────────────────────────────────────────────
        if (gen.canUpgrade()) {
            boolean canAfford = coins >= cost;
            inventory.setItem(SLOT_UPGRADE, item(
                canAfford ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                "<lime><bold>⬆ Upgrade → Level " + (gen.getLevel() + 1),
                "<gray>Kosten: " + (canAfford ? "<gold>" : "<red>") + costFmt + " Coins",
                "<gray>Deine Coins: <white>" + coinsFmt,
                canAfford ? "<green>Klicke zum Upgraden!" : "<red>Nicht genug Coins!"
            ));
        } else {
            inventory.setItem(SLOT_UPGRADE, item(
                Material.LIME_STAINED_GLASS_PANE,
                "<gold><bold>★ Maximales Level erreicht!",
                "<gray>Dieser Generator ist auf Level <yellow>" + gen.getLevel() + "<gray>.",
                "<green>Du hast das Maximum erreicht!"
            ));
        }

        // ── Auto-Sell Toggle ──────────────────────────────────────────────────
        inventory.setItem(SLOT_AUTOSELL, item(
            gen.isAutoSell() ? Material.LIME_DYE : Material.GRAY_DYE,
            gen.isAutoSell()
                ? "<green><bold>✔ Auto-Sell: AN"
                : "<gray><bold>✘ Auto-Sell: AUS",
            "<gray>Wenn aktiv: Items werden sofort",
            "<gray>für Coins verkauft.",
            " ",
            gen.isAutoSell()
                ? "<yellow>Klicke zum Deaktivieren."
                : "<yellow>Klicke zum Aktivieren."
        ));

        // ── Schließen ─────────────────────────────────────────────────────────
        inventory.setItem(SLOT_CLOSE, closeButton());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == SLOT_COLLECT) {
            plugin.getGeneratorManager().collectBuffer(player, gen);
            build(); // GUI aktualisieren
        } else if (slot == SLOT_UPGRADE) {
            if (gen.canUpgrade()) {
                plugin.getGeneratorManager().upgradeGenerator(player, gen);
                build();
            }
        } else if (slot == SLOT_AUTOSELL) {
            plugin.getGeneratorManager().toggleAutoSell(player, gen);
            build();
        } else if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    private String formatInterval(int ticks) {
        long ms = (long) ticks * 50L;
        return ms >= 1000 ? (ms / 1000) + "s" : ms + "ms";
    }
}
