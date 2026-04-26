package de.pinkhorizon.lobby.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.pinkhorizon.lobby.PHLobby;
import de.pinkhorizon.lobby.managers.ServerStatusManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class NavigatorCommand implements CommandExecutor, Listener {

    private record ServerEntry(String id, String displayName, Material icon, List<String> desc) {}

    private static final List<ServerEntry> SERVERS = List.of(
        new ServerEntry("survival",   "Survival",       Material.GRASS_BLOCK,   List.of("Erkunde die Welt!", "Economy, Claims & mehr")),
        new ServerEntry("smash",      "Smash the Boss", Material.NETHER_STAR,   List.of("Besiege endlos starke Bosse!", "Upgrades, Loot & mehr")),
        new ServerEntry("minigames",  "Minigames",      Material.DIAMOND_SWORD, List.of("BedWars & mehr")),
        new ServerEntry("skyblock",   "SkyBlock",       Material.MAGMA_BLOCK,   List.of("Baue deine eigene Insel!")),
        new ServerEntry("generators", "IdleForge",      Material.GOLD_BLOCK,    List.of("Platziere Generatoren!", "Verdiene passiv Geld & Prestige"))
    );

    // Slot-Positionen für 5 Server (Reihe 2: 3 Server, Reihe 3: 2 Server zentriert)
    private static final int[] SERVER_SLOTS = {10, 12, 14, 20, 22};

    private final PHLobby plugin;

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
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("Server-Navigator", TextColor.color(0xAA00AA)));

        // Hintergrund
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  pm   = pane.getItemMeta();
        pm.displayName(Component.empty());
        pane.setItemMeta(pm);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        ServerStatusManager ssm = plugin.getServerStatusManager();

        for (int i = 0; i < SERVERS.size(); i++) {
            ServerEntry entry   = SERVERS.get(i);
            ServerStatusManager.Status status  = ssm != null ? ssm.getStatus(entry.id())      : ServerStatusManager.Status.OFFLINE;
            int                        players = ssm != null ? ssm.getPlayerCount(entry.id()) : 0;
            inv.setItem(SERVER_SLOTS[i], buildServerItem(entry, status, players));
        }

        // Lobby-Button (unten rechts zentriert)
        inv.setItem(24, buildLobbyItem());

        player.openInventory(inv);
    }

    private ItemStack buildServerItem(ServerEntry entry, ServerStatusManager.Status status, int players) {
        boolean online      = status == ServerStatusManager.Status.ONLINE;
        boolean restarting  = status == ServerStatusManager.Status.RESTARTING;

        String nameColor = online ? "§a" : restarting ? "§e" : "§8";
        String statusLine = online
            ? "§a● Online §7– §f" + players + " §7Spieler"
            : restarting
                ? "§e● Neustart..."
                : "§c● Offline";

        ItemStack item = new ItemStack(entry.icon());
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(nameColor + entry.displayName()));

        List<String> lore = new ArrayList<>();
        lore.add("§8─────────────────────");
        entry.desc().forEach(d -> lore.add("§7" + d));
        lore.add("§8─────────────────────");
        lore.add(statusLine);
        lore.add(online ? "§7▶ Klicken zum Verbinden" : "§c✗ Derzeit nicht verfügbar");

        meta.lore(lore.stream().map(Component::text).toList());
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "server"),
            PersistentDataType.STRING, entry.id());
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "server_online"),
            PersistentDataType.BOOLEAN, online);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildLobbyItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("§d§lLobby"));
        meta.lore(List.of(Component.text("§7Du bist bereits in der Lobby")));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text("Server-Navigator", TextColor.color(0xAA00AA)))) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) return;

        String server = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "server"), PersistentDataType.STRING);
        if (server == null) return;

        Boolean online = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "server_online"), PersistentDataType.BOOLEAN);

        if (!Boolean.TRUE.equals(online)) {
            player.sendMessage("§c✗ Dieser Server ist gerade nicht verfügbar.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        player.closeInventory();
        player.sendMessage("§dVerbinde mit §f" + server + "§d...");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);

        @SuppressWarnings("UnstableApiUsage")
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }
}
