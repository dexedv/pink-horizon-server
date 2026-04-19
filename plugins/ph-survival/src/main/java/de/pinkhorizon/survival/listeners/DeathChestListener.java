package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class DeathChestListener implements Listener {

    private static final long EXPIRY_MS = 5 * 60 * 1000L; // 5 Minuten

    private final PHSurvival plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey expiryKey;

    public DeathChestListener(PHSurvival plugin) {
        this.plugin = plugin;
        this.ownerKey  = new NamespacedKey(plugin, "death_chest_owner");
        this.expiryKey = new NamespacedKey(plugin, "death_chest_expiry");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Kein Death-Chest bei aktivem KeepInventory
        if (plugin.getUpgradeManager().hasActiveKI(player.getUniqueId())) return;
        if (event.getDrops().isEmpty()) return;

        Location loc = findChestLocation(player.getLocation());
        if (loc == null) {
            player.sendMessage("§cKein Platz für Death-Chest – Items fallen auf den Boden!");
            return;
        }

        loc.getBlock().setType(Material.CHEST);
        Chest chestState = (Chest) loc.getBlock().getState();

        List<ItemStack> drops = List.copyOf(event.getDrops());
        for (ItemStack item : drops) {
            HashMap<Integer, ItemStack> leftover = chestState.getInventory().addItem(item);
            leftover.values().forEach(i -> loc.getWorld().dropItemNaturally(loc, i));
        }
        event.getDrops().clear();

        long expiry = System.currentTimeMillis() + EXPIRY_MS;
        chestState.getPersistentDataContainer().set(ownerKey,  PersistentDataType.STRING, player.getUniqueId().toString());
        chestState.getPersistentDataContainer().set(expiryKey, PersistentDataType.LONG,   expiry);
        chestState.update();

        player.sendMessage("§c☠ Death-Chest §7bei §fX:" + loc.getBlockX()
            + " Y:" + loc.getBlockY() + " Z:" + loc.getBlockZ());
        player.sendMessage("§7Exklusiv für dich für §f5 Minuten§7, danach öffentlich.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        Chest chest = (Chest) block.getState();
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;

        Player player = event.getPlayer();
        UUID owner = UUID.fromString(chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));
        long expiry = chest.getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);

        // Abgelaufen → öffentlich
        if (System.currentTimeMillis() > expiry) return;

        // Eigentümer oder OP → Zugriff
        if (player.getUniqueId().equals(owner) || player.isOp()) return;

        event.setCancelled(true);
        long remaining = (expiry - System.currentTimeMillis()) / 1000;
        player.sendMessage("§cDas ist ein Death-Chest! Noch §f" + remaining + "s §cbis er öffentlich wird.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;

        Chest chest = (Chest) block.getState();
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;

        Player player = event.getPlayer();
        UUID owner = UUID.fromString(chest.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING));
        long expiry = chest.getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);

        if (System.currentTimeMillis() > expiry) return;
        if (player.getUniqueId().equals(owner) || player.isOp()) return;

        event.setCancelled(true);
        player.sendMessage("§cDas ist ein Death-Chest und gehört einem anderen Spieler!");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest chest)) return;
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;
        if (!chest.getInventory().isEmpty()) return;

        // Leer → Truhe entfernen
        chest.getBlock().setType(Material.AIR);
    }

    private Location findChestLocation(Location death) {
        int x = death.getBlockX();
        int z = death.getBlockZ();
        for (int dy = 0; dy <= 3; dy++) {
            for (int dir : new int[]{0, 1, -1, 2, -2}) {
                Location candidate = new Location(death.getWorld(), x, death.getBlockY() + dy + dir, z);
                if (candidate.getBlockY() < 1 || candidate.getBlockY() > 255) continue;
                Block b = candidate.getBlock();
                if (!b.getType().isSolid() && b.getType() != Material.LAVA && b.getType() != Material.WATER) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
