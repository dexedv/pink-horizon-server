package de.pinkhorizon.lobby.listeners;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.pinkhorizon.lobby.PHLobby;
import de.pinkhorizon.lobby.managers.HotbarManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class HotbarListener implements Listener {

    private final PHLobby plugin;

    public HotbarListener(PHLobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        String action = item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, HotbarManager.KEY_ACTION),
                PersistentDataType.STRING);
        if (action == null) return;

        event.setCancelled(true);

        switch (action) {
            case "navigator"  -> plugin.getNavigatorCommand().openNavigator(player);
            case "playerlist" -> showPlayerList(player);
            case "rules"      -> showRules(player);
            case "cosmetics"  -> plugin.getCosmeticsManager().openGui(player);
        }
    }

    private void showPlayerList(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("━━━━ Online-Spieler (" + Bukkit.getOnlinePlayers().size() + ") ━━━━",
                TextColor.color(0xFF69B4)));
        Bukkit.getOnlinePlayers().forEach(p ->
                player.sendMessage(Component.text("  \u25b6 ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text(p.getName(), NamedTextColor.WHITE))));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━",
                TextColor.color(0xFF69B4)));
    }

    private void showRules(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("━━━━ Serverregeln ━━━━", TextColor.color(0xFF69B4)));
        player.sendMessage(Component.text("  1. \u00a7fKein Cheaten / Hacking", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  2. \u00a7fKein Griefing auf Survival", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  3. \u00a7fRespektvoller Umgang", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  4. \u00a7fKein Spam im Chat", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  5. \u00a7fAdmins haben das letzte Wort", NamedTextColor.GRAY));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", TextColor.color(0xFF69B4)));
    }

    // Items können nicht weggeworfen werden
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (!item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, HotbarManager.KEY_ACTION),
                PersistentDataType.STRING);
        if (action != null) event.setCancelled(true);
    }

    // Inventory-Klicks im eigenen Inventar verhindern
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Nur das eigene Spieler-Inventar sperren (kein Chest/Shop usw.)
        if (event.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) {
            event.setCancelled(true);
        }
    }
}
