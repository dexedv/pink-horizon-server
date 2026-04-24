package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.gui.HopperUpgradeGui;
import de.pinkhorizon.survival.managers.HopperUpgradeManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class HopperUpgradeListener implements Listener {

    private final PHSurvival plugin;
    private final HopperUpgradeGui gui;

    private final Map<String, String> pendingIds = new HashMap<>();

    public HopperUpgradeListener(PHSurvival plugin, HopperUpgradeGui gui) {
        this.plugin = plugin;
        this.gui    = gui;
    }

    private static String locKey(Block b) {
        return b.getWorld().getUID() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    // ── Transfer-Menge anpassen ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block block = getHopperBlock(event.getInitiator());
        if (block == null) return;

        int level = plugin.getHopperUpgradeManager().getLevel(block);
        if (level <= 1) return;

        int maxTransfer = HopperUpgradeManager.ITEMS_PER_TRANSFER[level];
        ItemStack item = event.getItem();
        int toTransfer = Math.min(maxTransfer, item.getAmount());
        if (toTransfer <= 1) return;

        ItemStack newItem = item.clone();
        newItem.setAmount(toTransfer);
        event.setItem(newItem);
    }

    private Block getHopperBlock(Inventory inv) {
        InventoryHolder holder = inv.getHolder(false);
        if (!(holder instanceof org.bukkit.block.Hopper hopper)) return null;
        Block block = hopper.getBlock();
        if (block.getType() != Material.HOPPER) return null;
        return block;
    }

    // ── Shift + Rechtsklick → GUI ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.HOPPER) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        event.setCancelled(true);
        gui.open(player, block);
    }

    // ── Trichter abgebaut ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.HOPPER) return;
        String id = plugin.getHopperUpgradeManager().removeAndGetId(block);
        if (id != null) pendingIds.put(locKey(block), id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        String id = pendingIds.remove(locKey(event.getBlock()));
        if (id == null) return;
        for (org.bukkit.entity.Item dropped : event.getItems()) {
            ItemStack stack = dropped.getItemStack();
            if (stack.getType() == Material.HOPPER) {
                plugin.getHopperUpgradeManager().applyToItem(stack, id);
                dropped.setItemStack(stack);
                break;
            }
        }
    }

    // ── Trichter platziert ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.HOPPER) return;
        String id = plugin.getHopperUpgradeManager().getIdFromItem(event.getItemInHand());
        if (id == null) return;
        plugin.getHopperUpgradeManager().placeWithId(block, id);
    }
}
