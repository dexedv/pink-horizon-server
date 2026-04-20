package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.TradeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TradeListener implements Listener {

    private final PHSurvival plugin;

    public TradeListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeManager.TradeHolder holder)) return;
        if (!holder.owner.equals(player.getUniqueId())) return;

        int slot = event.getSlot();
        boolean clickedTop = event.getClickedInventory() == event.getView().getTopInventory();

        // Always cancel clicks in middle separator area and partner's side
        if (clickedTop && slot >= 18) {
            event.setCancelled(true);

            if (slot == TradeManager.CONFIRM_SLOT) {
                boolean execute = plugin.getTradeManager().toggleConfirm(player.getUniqueId());
                if (execute) {
                    plugin.getTradeManager().executeTrade(player.getUniqueId());
                } else {
                    // Sync offer to partner
                    plugin.getTradeManager().syncPartnerOffer(player.getUniqueId());
                }
            } else if (slot == TradeManager.CANCEL_SLOT) {
                plugin.getTradeManager().cancelSession(player.getUniqueId());
            }
            return;
        }

        // Shift-click from own inventory (bottom) while trade GUI is open → move to own offer area (0-17)
        if (!clickedTop && event.isShiftClick()) {
            event.setCancelled(true);
            var item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) return;
            var inv = event.getView().getTopInventory();
            for (int i = 0; i < 18; i++) {
                var existing = inv.getItem(i);
                if (existing == null || existing.getType().isAir()) {
                    inv.setItem(i, item.clone());
                    player.getInventory().setItem(event.getSlot(), null);
                    plugin.getTradeManager().syncPartnerOffer(player.getUniqueId());
                    break;
                }
            }
            return;
        }

        // Any click in own offer area (0-17) → reset confirm state since offer changed
        if (clickedTop && slot < 18) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getTradeManager().syncPartnerOffer(player.getUniqueId()));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeManager.TradeHolder holder)) return;
        if (!holder.owner.equals(player.getUniqueId())) return;

        // Cancel drag if any dragged slot is outside the own offer area (0-17)
        for (int slot : event.getRawSlots()) {
            if (slot >= 18 && slot < TradeManager.INV_SIZE) {
                event.setCancelled(true);
                return;
            }
        }
        // If drag touches own offer area, sync after
        boolean touchesOffer = event.getRawSlots().stream().anyMatch(s -> s < 18);
        if (touchesOffer) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getTradeManager().syncPartnerOffer(player.getUniqueId()));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeManager.TradeHolder holder)) return;
        if (!holder.owner.equals(player.getUniqueId())) return;
        // Player closed trade GUI → cancel trade
        if (plugin.getTradeManager().isInSession(player.getUniqueId())) {
            plugin.getTradeManager().cancelSession(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getTradeManager().isInSession(player.getUniqueId())) {
            plugin.getTradeManager().cancelSession(player.getUniqueId());
        }
    }
}
