package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Auslöser für Job-Boni: Shift+F (Schleichen + Tauschen-Taste).
 * Normales F-Drücken bleibt vollständig unberührt.
 *
 * Außerdem: abgelaufene Bonus-Angeln werden beim Öffnen von Kisten,
 * beim Anklicken und beim Aufheben automatisch entfernt.
 */
public class JobBonusListener implements Listener {

    private final PHSurvival plugin;

    public JobBonusListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Shift+F – Bonus aktivieren ────────────────────────────────────────

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        event.setCancelled(true);
        plugin.getJobBonusManager().tryActivate(player);
    }

    // ── Kiste/Behälter öffnen → abgelaufene Angeln entfernen ─────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.PLAYER) return;
        var inv = event.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (plugin.getJobBonusManager().isExpiredTempRod(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }

    // ── Item in Inventar anklicken → abgelaufene Angel verschwinden lassen

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (!plugin.getJobBonusManager().isExpiredTempRod(item)) return;
        event.setCancelled(true);
        event.setCurrentItem(null);
        if (event.getWhoClicked() instanceof Player p) {
            p.sendActionBar(
                net.kyori.adventure.text.Component.text("§c✗ §7Die Bonus-Angel ist abgelaufen und verschwunden."));
        }
    }

    // ── Item vom Boden aufheben → abgelaufene Angel entfernen ────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Item dropped = event.getItem();
        if (!plugin.getJobBonusManager().isExpiredTempRod(dropped.getItemStack())) return;
        event.setCancelled(true);
        dropped.remove();
    }
}
