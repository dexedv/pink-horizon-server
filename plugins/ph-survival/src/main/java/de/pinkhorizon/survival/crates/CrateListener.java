package de.pinkhorizon.survival.crates;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class CrateListener implements Listener {

    private final PHSurvival plugin;
    private final CrateManager manager;

    public CrateListener(PHSurvival plugin, CrateManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // ── Chest interaction ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return;

        if (!manager.isCrate(block.getLocation())) return;

        // It's a registered crate → always cancel default chest open
        event.setCancelled(true);

        Player player = event.getPlayer();
        String crateType = manager.getCrateType(block.getLocation());

        // Check for key in hand
        ItemStack hand = player.getInventory().getItemInMainHand();
        String keyType = manager.getKeyType(hand);

        if (keyType == null) {
            String crateName = CrateManager.CRATE_NAMES.getOrDefault(crateType, "Truhe");
            String keyName = switch (crateType) {
                case "eco"     -> "§6Eco-Schlüssel";
                case "claims"  -> "§aClaims-Schlüssel";
                case "spawner" -> "§dSpawner-Schlüssel";
                default        -> "Schlüssel";
            };
            player.sendMessage(Component.text("Du brauchst einen " + keyName + "§7 für die " + crateName + "!", NamedTextColor.GRAY));
            return;
        }

        if (!keyType.equals(crateType)) {
            String neededKey = switch (crateType) {
                case "eco"     -> "§6Eco-Schlüssel";
                case "claims"  -> "§aClaims-Schlüssel";
                case "spawner" -> "§dSpawner-Schlüssel";
                default        -> "den passenden Schlüssel";
            };
            player.sendMessage(Component.text("Falscher Schlüssel! Du brauchst den ", NamedTextColor.RED)
                .append(Component.text(neededKey).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            return;
        }

        if (manager.hasActiveAnimation(player.getUniqueId())) {
            player.sendMessage(Component.text("Du öffnest gerade eine Truhe!", NamedTextColor.RED));
            return;
        }

        // Consume one key
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Pick reward and start animation
        CrateReward reward = manager.getRandomReward(crateType);
        CrateAnimation anim = new CrateAnimation(plugin, player, crateType, reward, manager);
        manager.addActiveAnimation(player.getUniqueId(), anim);
        anim.start();
    }

    // ── Prevent item theft from animation GUI ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CrateAnimation.Holder) {
            event.setCancelled(true);
        }
    }

    // ── Key refund on early close ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrateAnimation.Holder holder)) return;
        CrateAnimation anim = holder.getAnimation();
        if (anim.isFinished() || anim.isCancelled()) return;

        // Player closed early → cancel and refund key
        anim.cancel();

        if (event.getPlayer() instanceof Player player) {
            ItemStack refund = manager.createKey(anim.getCrateType());
            var leftover = player.getInventory().addItem(refund);
            leftover.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(Component.text("Animation abgebrochen – dein Schlüssel wurde zurückgegeben.",
                TextColor.color(0xFFAA00)));
        }
    }
}
