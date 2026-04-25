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
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FurnaceUpgradeListener implements Listener {

    private static final Set<Material> FURNACE_TYPES = EnumSet.of(
        Material.FURNACE,
        Material.BLAST_FURNACE,
        Material.SMOKER
    );

    private final PHSurvival plugin;
    private final FurnaceUpgradeGui gui;

    // locKey → furnace UUID; gesetzt in onBlockBreak, gelesen in onBlockDrop
    private final Map<String, String> pendingIds = new HashMap<>();

    public FurnaceUpgradeListener(PHSurvival plugin, FurnaceUpgradeGui gui) {
        this.plugin = plugin;
        this.gui    = gui;
    }

    private static String locKey(Block b) {
        return b.getWorld().getUID() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    // ── Schmelzzeit ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmeltStart(FurnaceStartSmeltEvent event) {
        int level = plugin.getFurnaceUpgradeManager().getLevel(event.getBlock());
        if (level <= 1) return;
        event.setTotalCookTime(plugin.getFurnaceUpgradeManager().getCookTicks(event.getBlock()));
    }

    // ── Doppel-Output (Fortune-Upgrade) ──────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        int chancePct = plugin.getFurnaceUpgradeManager().getFortuneChancePct(event.getBlock());
        if (chancePct <= 0) return;
        if (Math.random() * 100 < chancePct) {
            ItemStack result = event.getResult().clone();
            result.setAmount(Math.min(result.getAmount() * 2, result.getMaxStackSize()));
            event.setResult(result);
        }
    }

    // ── Shift + Rechtsklick → GUI ─────────────────────────────────────────

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

    // ── Ofen abgebaut: UUID merken, aus Cache entfernen ──────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!FURNACE_TYPES.contains(block.getType())) return;
        String id = plugin.getFurnaceUpgradeManager().removeAndGetId(block);
        if (id != null) {
            pendingIds.put(locKey(block), id);
        }
    }

    // ── UUID auf den gedropten Ofen schreiben ─────────────────────────────
    // Kein block.getType()-Check hier – Block ist zu diesem Zeitpunkt bereits AIR

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        String id = pendingIds.remove(locKey(event.getBlock()));
        if (id == null) return;
        for (org.bukkit.entity.Item dropped : event.getItems()) {
            ItemStack stack = dropped.getItemStack();
            if (FURNACE_TYPES.contains(stack.getType())) {
                plugin.getFurnaceUpgradeManager().applyToItem(stack, id);
                dropped.setItemStack(stack);
                break;
            }
        }
    }

    // ── Ofen platziert: UUID vom Item laden ───────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!FURNACE_TYPES.contains(block.getType())) return;
        String id = plugin.getFurnaceUpgradeManager().getIdFromItem(event.getItemInHand());
        if (id == null) return;
        plugin.getFurnaceUpgradeManager().placeWithId(block, id);
    }
}
