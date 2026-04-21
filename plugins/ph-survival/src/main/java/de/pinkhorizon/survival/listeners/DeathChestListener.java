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
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathChestListener implements Listener {

    private static final long EXPIRY_MS = 5 * 60 * 1000L; // 5 Minuten

    private final PHSurvival plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey expiryKey;
    private final NamespacedKey ownerNameKey;
    private final Map<String, BukkitTask> countdownTasks = new HashMap<>();

    public DeathChestListener(PHSurvival plugin) {
        this.plugin       = plugin;
        this.ownerKey     = new NamespacedKey(plugin, "death_chest_owner");
        this.expiryKey    = new NamespacedKey(plugin, "death_chest_expiry");
        this.ownerNameKey = new NamespacedKey(plugin, "death_chest_owner_name");
    }

    private String holoKey(Location loc) {
        return "dc_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Kein Death-Chest bei aktivem KeepInventory
        if (plugin.getUpgradeManager().hasActiveKI(player.getUniqueId())) return;
        if (event.getDrops().isEmpty()) return;

        Location loc = findChestLocation(player.getLocation());
        if (loc == null) {
            player.sendMessage("§cKein freier Platz für eine Todeskiste – deine Items fallen auf den Boden!");
            return;
        }

        // Truhe platzieren und PDC speichern (Truhe ist noch leer – update() überschreibt kein Inventar)
        loc.getBlock().setType(Material.CHEST);
        Chest chestState = (Chest) loc.getBlock().getState();
        long expiry = System.currentTimeMillis() + EXPIRY_MS;
        chestState.getPersistentDataContainer().set(ownerKey,     PersistentDataType.STRING, player.getUniqueId().toString());
        chestState.getPersistentDataContainer().set(expiryKey,    PersistentDataType.LONG,   expiry);
        chestState.getPersistentDataContainer().set(ownerNameKey, PersistentDataType.STRING, player.getName());
        chestState.update();

        // Items erst NACH update() in das Live-Inventar der Truhe legen
        Chest liveChest = (Chest) loc.getBlock().getState();
        List<ItemStack> drops = List.copyOf(event.getDrops());
        event.getDrops().clear();
        for (ItemStack item : drops) {
            if (item == null || item.getType().isAir()) continue;
            HashMap<Integer, ItemStack> leftover = liveChest.getInventory().addItem(item.clone());
            leftover.values().forEach(i -> loc.getWorld().dropItemNaturally(loc, i));
        }

        // Hologramm über der Truhe spawnen
        final String key = holoKey(loc);
        Location holoLoc = loc.clone().add(0.5, 1.8, 0.5);
        plugin.getHologramManager().createTemporary(key, holoLoc, List.of(
            "<red><bold>☠ Todeskiste</bold></red>",
            "<gray>Besitzer: <white>" + player.getName() + "</white></gray>",
            "<yellow>Öffentlich in: 5m 0s</yellow>"
        ), 0.85f);

        // Countdown-Task: jede Sekunde die dritte Zeile aktualisieren
        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long remaining = expiry - System.currentTimeMillis();
            if (remaining <= 0) {
                plugin.getHologramManager().updateLine(key, 2, "<green>✓ Jetzt öffentlich zugänglich</green>");
                taskRef[0].cancel();
                countdownTasks.remove(key);
                return;
            }
            long mins = remaining / 60000;
            long secs = (remaining % 60000) / 1000;
            plugin.getHologramManager().updateLine(key, 2,
                "<yellow>Öffentlich in: " + mins + "m " + secs + "s</yellow>");
        }, 20L, 20L);
        countdownTasks.put(key, taskRef[0]);

        player.sendMessage("§c☠ Todeskiste §7erstellt bei §fX:" + loc.getBlockX()
            + " Y:" + loc.getBlockY() + " Z:" + loc.getBlockZ());
        player.sendMessage("§75 Minuten lang nur für dich zugänglich§7, danach für alle.");
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
        player.sendMessage("§cDas ist eine Todeskiste! Noch §f" + remaining + " §cSekunden bis sie öffentlich wird.");
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

        if (System.currentTimeMillis() > expiry) {
            removeHologram(block.getLocation());
            return;
        }
        if (player.getUniqueId().equals(owner) || player.isOp()) {
            removeHologram(block.getLocation());
            return;
        }

        event.setCancelled(true);
        player.sendMessage("§cDiese Todeskiste gehört einem anderen Spieler!");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest chest)) return;
        if (!chest.getPersistentDataContainer().has(ownerKey)) return;
        if (!chest.getInventory().isEmpty()) return;

        removeHologram(chest.getBlock().getLocation());
        chest.getBlock().setType(Material.AIR);
    }

    private void removeHologram(Location loc) {
        String key = holoKey(loc);
        BukkitTask task = countdownTasks.remove(key);
        if (task != null) task.cancel();
        plugin.getHologramManager().remove(key);
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
