package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public class ClaimProtectionListener implements Listener {

    private final PHSurvival plugin;

    public ClaimProtectionListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isBlocked(player, event.getBlock().getChunk())) {
            event.setCancelled(true);
            player.sendMessage("\u00a7cDieser Chunk geh\u00f6rt jemand anderem!");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isBlocked(player, event.getBlock().getChunk())) {
            event.setCancelled(true);
            player.sendMessage("\u00a7cDieser Chunk geh\u00f6rt jemand anderem!");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Material type = event.getClickedBlock().getType();
        if (!isInteractable(type)) return;

        Player player = event.getPlayer();
        if (isBlocked(player, event.getClickedBlock().getChunk())) {
            event.setCancelled(true);
            player.sendMessage("\u00a7cDieser Chunk geh\u00f6rt jemand anderem!");
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Villager)) return;
        Player player = event.getPlayer();
        Chunk chunk = event.getRightClicked().getLocation().getChunk();
        if (isBlocked(player, chunk)) {
            event.setCancelled(true);
            player.sendMessage("\u00a7cDu kannst diesen Villager nicht nutzen – der Chunk geh\u00f6rt jemand anderem!");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;

        Chunk chunk = victim.getLocation().getChunk();
        if (!plugin.getClaimManager().isClaimed(chunk)) return;
        // PvP in claimed chunks is blocked unless survival.admin
        if (attacker.hasPermission("survival.admin")) return;
        event.setCancelled(true);
        attacker.sendMessage("\u00a7cPvP ist in geclaimten Chunks nicht erlaubt!");
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block ->
                plugin.getClaimManager().isClaimed(block.getChunk())
        );
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block ->
                plugin.getClaimManager().isClaimed(block.getChunk())
        );
    }

    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player)) return;
        Chunk chunk = event.getEntity().getLocation().getChunk();
        if (isBlocked(player, chunk)) {
            event.setCancelled(true);
            player.sendMessage("\u00a7cDieser Chunk geh\u00f6rt jemand anderem!");
        }
    }

    private boolean isInteractable(Material type) {
        return switch (type) {
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX,
                 WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX,
                 PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX,
                 CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX,
                 FURNACE, BLAST_FURNACE, SMOKER, CRAFTING_TABLE, ANVIL, CHIPPED_ANVIL,
                 DAMAGED_ANVIL, ENCHANTING_TABLE, BREWING_STAND,
                 OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR, ACACIA_DOOR,
                 DARK_OAK_DOOR, MANGROVE_DOOR, CHERRY_DOOR, BAMBOO_DOOR, CRIMSON_DOOR,
                 WARPED_DOOR, IRON_DOOR,
                 OAK_TRAPDOOR, SPRUCE_TRAPDOOR, BIRCH_TRAPDOOR, JUNGLE_TRAPDOOR,
                 ACACIA_TRAPDOOR, DARK_OAK_TRAPDOOR, MANGROVE_TRAPDOOR, CHERRY_TRAPDOOR,
                 BAMBOO_TRAPDOOR, CRIMSON_TRAPDOOR, WARPED_TRAPDOOR, IRON_TRAPDOOR,
                 OAK_FENCE_GATE, SPRUCE_FENCE_GATE, BIRCH_FENCE_GATE, JUNGLE_FENCE_GATE,
                 ACACIA_FENCE_GATE, DARK_OAK_FENCE_GATE, MANGROVE_FENCE_GATE,
                 CHERRY_FENCE_GATE, BAMBOO_FENCE_GATE, CRIMSON_FENCE_GATE, WARPED_FENCE_GATE,
                 STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON, JUNGLE_BUTTON,
                 ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON, CHERRY_BUTTON,
                 BAMBOO_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON, POLISHED_BLACKSTONE_BUTTON,
                 LEVER, HOPPER, DROPPER, DISPENSER -> true;
            default -> false;
        };
    }

    private boolean isBlocked(Player player, Chunk chunk) {
        if (player.hasPermission("survival.admin")) return false;
        if (!plugin.getClaimManager().isClaimed(chunk)) return false;
        UUID uuid = player.getUniqueId();
        return !plugin.getClaimManager().isTrusted(chunk, uuid);
    }
}
