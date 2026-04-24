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
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Listener für das Ofen-Upgrade-System.
 *
 * Upgrade-Daten werden im In-Memory-Cache + DB (zuverlässig) gespeichert.
 * Beim Abbau wird das Level per Item-PDC auf den Drop geschrieben;
 * beim Platzieren wird es zurück in Cache + DB geladen.
 * So bleiben Upgrades auch beim Umsetzen des Ofens erhalten.
 */
public class FurnaceUpgradeListener implements Listener {

    private static final Set<Material> FURNACE_TYPES = EnumSet.of(
        Material.FURNACE,
        Material.BLAST_FURNACE,
        Material.SMOKER
    );

    private final PHSurvival plugin;
    private final FurnaceUpgradeGui gui;

    // Zwischenspeicher: locKey → level, gesetzt in onBlockBreak, gelesen in onBlockDrop
    private final Map<String, Integer> pendingLevels = new HashMap<>();

    public FurnaceUpgradeListener(PHSurvival plugin, FurnaceUpgradeGui gui) {
        this.plugin = plugin;
        this.gui    = gui;
    }

    private static String locKey(Block b) {
        return b.getWorld().getUID() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    // ── Schmelzzeit anpassen ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmeltStart(FurnaceStartSmeltEvent event) {
        int level = plugin.getFurnaceUpgradeManager().getLevel(event.getBlock());
        if (level <= 1) return;
        event.setTotalCookTime(plugin.getFurnaceUpgradeManager().getCookTicks(event.getBlock()));
    }

    // ── Shift + Rechtsklick → GUI öffnen ─────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !FURNACE_TYPES.contains(block.getType())) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        event.setCancelled(true);
        gui.open(player, block);
    }

    // ── Ofen abgebaut: Level aus Cache lesen, für Drop merken ────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!FURNACE_TYPES.contains(block.getType())) return;
        int level = plugin.getFurnaceUpgradeManager().getLevel(block);
        plugin.getFurnaceUpgradeManager().remove(block);
        if (level > 1) {
            pendingLevels.put(locKey(block), level);
        }
    }

    // ── Level auf den gedropten Ofen schreiben ───────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Block block = event.getBlock();
        if (!FURNACE_TYPES.contains(block.getType())) return;
        Integer level = pendingLevels.remove(locKey(block));
        if (level == null || level <= 1) return;
        for (org.bukkit.entity.Item dropped : event.getItems()) {
            ItemStack stack = dropped.getItemStack();
            if (FURNACE_TYPES.contains(stack.getType())) {
                plugin.getFurnaceUpgradeManager().applyLevelToItem(stack, level);
                dropped.setItemStack(stack);
            }
        }
    }

    // ── Ofen platziert: Level vom Item in Cache + DB laden ───────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!FURNACE_TYPES.contains(block.getType())) return;
        ItemStack item = event.getItemInHand();
        int level = plugin.getFurnaceUpgradeManager().getLevelFromItem(item);
        if (level <= 1) return;
        plugin.getFurnaceUpgradeManager().setLevel(block, level);
    }
}
