package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.gui.ClaimMapGui;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ClaimMapListener implements Listener {

    private final PHSurvival plugin;

    public ClaimMapListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ClaimMapGui gui)) return;
        if (!gui.playerUuid.equals(player.getUniqueId())) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();

        NamespacedKey keyNav = new NamespacedKey(plugin, ClaimMapGui.KEY_NAV);
        NamespacedKey keyCX  = new NamespacedKey(plugin, ClaimMapGui.KEY_CX);
        NamespacedKey keyCZ  = new NamespacedKey(plugin, ClaimMapGui.KEY_CZ);

        // Navigation button?
        String nav = meta.getPersistentDataContainer().get(keyNav, PersistentDataType.STRING);
        if (nav != null) {
            int newOffX = gui.offX;
            int newOffZ = gui.offZ;
            switch (nav) {
                case "w" -> newOffX -= 4;
                case "e" -> newOffX += 4;
                case "n" -> newOffZ -= 4;
                case "s" -> newOffZ += 4;
                case "c" -> { newOffX = 0; newOffZ = 0; }
            }
            final int fx = newOffX, fz = newOffZ;
            plugin.getServer().getScheduler().runTask(plugin, () ->
                ClaimMapGui.open(player, plugin, fx, fz));
            return;
        }

        // Chunk tile?
        Integer cx = meta.getPersistentDataContainer().get(keyCX, PersistentDataType.INTEGER);
        Integer cz = meta.getPersistentDataContainer().get(keyCZ, PersistentDataType.INTEGER);
        if (cx == null || cz == null) return;

        String worldName = player.getWorld().getName();
        boolean claimed = plugin.getClaimManager().isClaimedAt(worldName, cx, cz);

        if (claimed) {
            if (!plugin.getClaimManager().getOwnerAt(worldName, cx, cz).equals(player.getUniqueId())) {
                player.sendMessage("§cDas ist nicht dein Claim!");
                return;
            }
            Chunk chunk = player.getWorld().getChunkAt(cx, cz);
            plugin.getClaimManager().unclaim(chunk, player.getUniqueId());
            player.sendMessage("§7Claim §c" + cx + "§7, §c" + cz + " §7entfernt.");
        } else {
            // Spawn-Schutzzone prüfen (identisch mit ClaimCommand)
            if (!player.isOp()) {
                Location spawnLoc = getSpawnLocation();
                if (spawnLoc != null && spawnLoc.getWorld() != null
                        && spawnLoc.getWorld().getName().equals(worldName)) {
                    double ccx = cx * 16 + 8;
                    double ccz = cz * 16 + 8;
                    double dist = Math.sqrt(Math.pow(ccx - spawnLoc.getX(), 2)
                            + Math.pow(ccz - spawnLoc.getZ(), 2));
                    if (dist <= 200) {
                        player.sendMessage("§cIm Spawn-Bereich (200 Blöcke) darf nicht geclaimed werden!");
                        return;
                    }
                }
            }

            long price = plugin.getConfig().getLong("claims.claim-price", 100);
            if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), price)) {
                player.sendMessage("§cNicht genug Coins! Preis: §f" + price);
                return;
            }

            int maxClaims = plugin.getRankManager().getMaxClaims(player.getUniqueId());
            Chunk chunk = player.getWorld().getChunkAt(cx, cz);
            boolean ok = plugin.getClaimManager().claim(chunk, player.getUniqueId(), maxClaims);
            if (ok) {
                player.sendMessage("§aChunk §f" + cx + "§7, §f" + cz + " §ageclaimt! §7(-" + price + " Coins)");
            } else {
                plugin.getEconomyManager().deposit(player.getUniqueId(), price); // Rückerstattung
                int count = plugin.getClaimManager().getClaimCount(player.getUniqueId());
                player.sendMessage("§cClaim nicht möglich! (" + count + "/" + maxClaims + ")");
            }
        }

        // Refresh the map
        plugin.getServer().getScheduler().runTask(plugin, () ->
            ClaimMapGui.open(player, plugin, gui.offX, gui.offZ));
    }

    private Location getSpawnLocation() {
        String worldName = plugin.getConfig().getString("spawn.world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
            plugin.getConfig().getDouble("spawn.x"),
            plugin.getConfig().getDouble("spawn.y"),
            plugin.getConfig().getDouble("spawn.z"));
    }
}
