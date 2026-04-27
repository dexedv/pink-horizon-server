package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.gui.GuiBase;
import de.pinkhorizon.skyblock.gui.MainMenuGui;
import de.pinkhorizon.skyblock.gui.QuestGui;
import de.pinkhorizon.skyblock.managers.NpcManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Zentrale GUI-Listener-Klasse:
 * - Leitet Inventory-Klicks an das richtige GuiBase weiter.
 * - Öffnet GUIs bei NPC-Interaktion.
 */
public class GuiListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;

    public GuiListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    // ── Inventory-Klick ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GuiBase gui)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType().isAir()) return;

        gui.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiBase) {
            event.setCancelled(true);
        }
    }

    // ── NPC-Interaktion ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Villager villager)) return;

        String npcType = plugin.getNpcManager().getNpcType(villager);
        if (npcType == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        switch (npcType) {
            case "quest-master" -> {
                plugin.getQuestManager().loadPlayer(player.getUniqueId()); // Refresh
                new QuestGui(plugin, player).open(player);
            }
            case "generator-shop" -> {
                // Shop: gibt einen kostenlosen Start-Generator
                giveFreeGenerator(player);
            }
            case "achievement-master" -> {
                new de.pinkhorizon.skyblock.gui.AchievementGui(plugin, player).open(player);
            }
            case "title-shop" -> {
                new de.pinkhorizon.skyblock.gui.TitleGui(plugin, player).open(player);
            }
            default -> {
                new MainMenuGui(plugin, player).open(player);
            }
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private void giveFreeGenerator(Player player) {
        var item = plugin.getGeneratorManager().createGeneratorItem();
        var overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<green>Du hast einen <gold>Generator <green>erhalten!"
            + " <gray>Platziere ihn auf deiner Insel."));
        player.playSound(player.getLocation(),
            org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
    }
}
