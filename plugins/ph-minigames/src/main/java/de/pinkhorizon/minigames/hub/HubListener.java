package de.pinkhorizon.minigames.hub;

import de.pinkhorizon.minigames.PHMinigames;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

public class HubListener implements Listener {

    private final PHMinigames plugin;

    public HubListener(PHMinigames plugin) {
        this.plugin = plugin;
    }

    private boolean inHub(Player player) {
        return plugin.getArenaManager().getGameOf(player.getUniqueId()) == null;
    }

    /** Nur Adventure-Spieler im Hub einschränken – Creative/OP dürfen alles. */
    private boolean shouldRestrict(Player player) {
        return inHub(player) && player.getGameMode() != org.bukkit.GameMode.CREATIVE;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("[Hub] " + player.getName() + " joined – setup in 5 ticks");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean hub = inHub(player);
            plugin.getLogger().info("[Hub] setup task: " + player.getName() + " online=" + player.isOnline() + " inHub=" + hub);
            if (player.isOnline() && hub) {
                plugin.getHubManager().setupHubPlayer(player);
                plugin.getLogger().info("[Hub] setupHubPlayer done for " + player.getName());
            }
        }, 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && inHub(player)) {
                plugin.getHubManager().setupHubPlayer(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!inHub(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!inHub(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!shouldRestrict(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!shouldRestrict(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!shouldRestrict(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onCompassClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!inHub(player)) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.getHubManager().isHubCompass(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        plugin.getHubGui().open(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location hubSpawn = plugin.getHubManager().getHubSpawn();
        if (hubSpawn == null) return;
        if (!event.getLocation().getWorld().equals(hubSpawn.getWorld())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.contains("Spiele wählen")) return;
        event.setCancelled(true);
        if (event.getRawSlot() >= 0 && event.getRawSlot() < 27) {
            plugin.getHubGui().handleClick(player, event.getRawSlot());
        }
    }
}
