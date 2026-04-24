package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.gui.FurnaceUpgradeGui;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.Set;

/**
 * Listener für das Ofen-Upgrade-System.
 *
 * Bedienung: Shift + Rechtsklick auf Ofen / Hochofen / Räucherofen → Upgrade-GUI
 */
public class FurnaceUpgradeListener implements Listener {

    private static final Set<Material> FURNACE_TYPES = EnumSet.of(
        Material.FURNACE,
        Material.BLAST_FURNACE,
        Material.SMOKER
    );

    private final PHSurvival plugin;
    private final FurnaceUpgradeGui gui;

    public FurnaceUpgradeListener(PHSurvival plugin, FurnaceUpgradeGui gui) {
        this.plugin = plugin;
        this.gui    = gui;
    }

    // ── Schmelzzeit anpassen ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmeltStart(FurnaceStartSmeltEvent event) {
        int level = plugin.getFurnaceUpgradeManager().getLevel(event.getBlock());
        if (level <= 1) return; // kein Upgrade → keine Änderung
        int ticks = plugin.getFurnaceUpgradeManager().getCookTicks(event.getBlock());
        event.setTotalCookTime(ticks);
    }

    // ── Shift + Rechtsklick → GUI öffnen ─────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        // Nur Haupt-Hand, nur Rechtsklick auf Block
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !FURNACE_TYPES.contains(block.getType())) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        // Shift-Rechtsklick auf Ofen → GUI statt normaler Öffnung
        event.setCancelled(true);
        gui.open(player, block);
    }

    // ── Ofen abgebaut → Upgrade-Daten löschen ────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!FURNACE_TYPES.contains(block.getType())) return;
        plugin.getFurnaceUpgradeManager().remove(block);
    }
}
