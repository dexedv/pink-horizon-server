package de.pinkhorizon.lobby.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class NavigatorCommand implements CommandExecutor, Listener {

    private final PHLobby plugin;
    private static final String INV_TITLE = "\u00a75\u00a7lServer-Navigator";

    public NavigatorCommand(PHLobby plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        openNavigator(player);
        return true;
    }

    public void openNavigator(Player player) {
        Inventory inv = plugin.getServer().createInventory(null, 27, Component.text("Server-Navigator", TextColor.color(0xAA00AA)));

        inv.setItem(10, buildItem(Material.GRASS_BLOCK,    "\u00a7aSurvival",   List.of("\u00a77Erkunde die Welt!", "\u00a77Economy, Claims & mehr"), "survival"));
        inv.setItem(12, buildItem(Material.MAGMA_BLOCK,    "\u00a76SkyBlock",   List.of("\u00a77Baue deine eigene Insel!"), "skyblock"));
        inv.setItem(14, buildItem(Material.DIAMOND_SWORD,  "\u00a7bMinigames",  List.of("\u00a77BedWars, SkyWars & mehr!"), "minigames"));
        inv.setItem(16, buildItem(Material.NETHER_STAR,    "\u00a7dLobby",      List.of("\u00a77Zurueck zum Hub"), "lobby"));

        // Deko-Glasscheiben
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = filler.getItemMeta();
        m.displayName(Component.text(" "));
        filler.setItemMeta(m);
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        player.openInventory(inv);
    }

    private ItemStack buildItem(Material mat, String name, List<String> lore, String serverTag) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(lore.stream().map(Component::text).toList());
        // Server-Tag im PDC speichern
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "server"),
                org.bukkit.persistence.PersistentDataType.STRING,
                serverTag
        );
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().title().equals(Component.text("Server-Navigator", TextColor.color(0xAA00AA)))) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta == null) return;

            String server = meta.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(plugin, "server"),
                    org.bukkit.persistence.PersistentDataType.STRING
            );
            if (server == null) return;

            player.closeInventory();
            player.sendMessage("\u00a7dVerbinde mit \u00a7f" + server + "\u00a7d...");

            @SuppressWarnings("UnstableApiUsage")
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
    }
}
