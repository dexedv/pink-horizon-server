package de.pinkhorizon.lobby.commands;

import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BorderCommand implements CommandExecutor, TabCompleter, Listener {

    private final PHLobby plugin;
    private final NamespacedKey wandKey;

    // Pro Spieler: Liste der markierten Punkte (max 4)
    private final Map<UUID, List<Location>> selections = new HashMap<>();

    public BorderCommand(PHLobby plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "border_wand");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }
        if (!player.hasPermission("lobby.admin")) {
            player.sendMessage("\u00a7cKeine Berechtigung!");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "help";

        switch (sub) {
            case "wand" -> {
                player.getInventory().addItem(buildWand());
                player.sendMessage(prefix().append(Component.text(
                    "Wand erhalten! Rechtsklick auf einen Block = Punkt setzen.", NamedTextColor.GREEN)));
            }
            case "clear" -> {
                selections.remove(player.getUniqueId());
                player.sendMessage(prefix().append(Component.text("Auswahl geleert.", NamedTextColor.YELLOW)));
            }
            case "create" -> createBorder(player);
            case "remove" -> removeBorder(player);
            case "info"   -> showInfo(player);
            default -> {
                player.sendMessage(prefix().append(Component.text(
                    "/border <wand | create | remove | clear | info>", NamedTextColor.GRAY)));
            }
        }
        return true;
    }

    // ── Wand-Klick: Punkt hinzufügen ────────────────────────────────────

    @EventHandler
    public void onWandClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        event.setCancelled(true);

        Location loc = player.getLocation();
        List<Location> points = selections.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());

        if (points.size() >= 4) {
            player.sendMessage(prefix().append(Component.text(
                "Bereits 4 Punkte gesetzt. Nutze /border create oder /border clear.", NamedTextColor.RED)));
            return;
        }

        points.add(loc.clone());
        int num = points.size();

        player.sendMessage(prefix().append(Component.text(
            "Punkt " + num + " gesetzt: X=" + (int) loc.getX() + " Z=" + (int) loc.getZ(),
            NamedTextColor.GREEN)));

        player.sendActionBar(Component.text(
            "\u00a7d" + num + "/4 Punkte gesetzt" +
            (num == 4 ? " \u2714 Tippe /border create" : ""), NamedTextColor.LIGHT_PURPLE));

        // Partikel am Punkt
        player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
            loc.clone().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0);
    }

    // ── Border erstellen ────────────────────────────────────────────────

    private void createBorder(Player player) {
        List<Location> points = selections.get(player.getUniqueId());

        if (points == null || points.size() < 2) {
            player.sendMessage(prefix().append(Component.text(
                "Mindestens 2 Punkte mit dem Wand markieren! (/border wand)", NamedTextColor.RED)));
            return;
        }

        // Bounding Box berechnen
        double minX = points.stream().mapToDouble(Location::getX).min().getAsDouble();
        double maxX = points.stream().mapToDouble(Location::getX).max().getAsDouble();
        double minZ = points.stream().mapToDouble(Location::getZ).min().getAsDouble();
        double maxZ = points.stream().mapToDouble(Location::getZ).max().getAsDouble();

        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double sizeX   = maxX - minX;
        double sizeZ   = maxZ - minZ;
        double size    = Math.max(sizeX, sizeZ) + 4; // +4 Puffer

        World world = player.getWorld();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamageAmount(2.0);     // Schaden pro Sekunde wenn außerhalb
        border.setDamageBuffer(0.0);     // Kein Puffer – sofort Schaden
        border.setWarningDistance(5);    // Rote Vignette ab 5 Blöcken Entfernung
        border.setWarningTime(5);

        // Config speichern
        plugin.getConfig().set("border.centerX", centerX);
        plugin.getConfig().set("border.centerZ", centerZ);
        plugin.getConfig().set("border.size", size);
        plugin.getConfig().set("border.enabled", true);
        plugin.saveConfig();

        selections.remove(player.getUniqueId());

        player.sendMessage(prefix().append(Component.text("Border gesetzt!", NamedTextColor.GREEN)));
        player.sendMessage(Component.text(
            "  Mitte: X=" + String.format("%.1f", centerX) + " Z=" + String.format("%.1f", centerZ),
            NamedTextColor.GRAY));
        player.sendMessage(Component.text(
            "  Groesse: " + String.format("%.1f", size) + " Bloecke",
            NamedTextColor.GRAY));
    }

    // ── Border entfernen ────────────────────────────────────────────────

    private void removeBorder(Player player) {
        World world = player.getWorld();
        WorldBorder border = world.getWorldBorder();
        border.reset();

        plugin.getConfig().set("border.enabled", false);
        plugin.saveConfig();

        player.sendMessage(prefix().append(Component.text("Border entfernt.", NamedTextColor.YELLOW)));
    }

    // ── Info anzeigen ───────────────────────────────────────────────────

    private void showInfo(Player player) {
        WorldBorder border = player.getWorld().getWorldBorder();
        List<Location> points = selections.getOrDefault(player.getUniqueId(), List.of());

        player.sendMessage(prefix().append(Component.text("=== Border Info ===", TextColor.color(0xFF69B4))));
        player.sendMessage(Component.text("  Mitte:   X=" + String.format("%.1f", border.getCenter().getX())
            + " Z=" + String.format("%.1f", border.getCenter().getZ()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Groesse: " + String.format("%.1f", border.getSize()) + " Bloecke",
            NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Punkte in Auswahl: " + points.size() + "/4", NamedTextColor.GRAY));
        for (int i = 0; i < points.size(); i++) {
            Location l = points.get(i);
            player.sendMessage(Component.text("    Punkt " + (i + 1) + ": X=" + (int) l.getX()
                + " Z=" + (int) l.getZ(), NamedTextColor.WHITE));
        }
    }

    // ── Border beim Start laden ─────────────────────────────────────────

    public void loadSavedBorder(World world) {
        if (!plugin.getConfig().getBoolean("border.enabled", false)) return;
        double cx   = plugin.getConfig().getDouble("border.centerX");
        double cz   = plugin.getConfig().getDouble("border.centerZ");
        double size = plugin.getConfig().getDouble("border.size");

        WorldBorder border = world.getWorldBorder();
        border.setCenter(cx, cz);
        border.setSize(size);
        border.setDamageAmount(2.0);
        border.setDamageBuffer(0.0);
        border.setWarningDistance(5);
        border.setWarningTime(5);
        plugin.getLogger().info("Border geladen: Mitte X=" + cx + " Z=" + cz + " Groesse=" + size);
    }

    // ── Hilfsmethoden ───────────────────────────────────────────────────

    private ItemStack buildWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text("\u00a7d\u00a7lBorder-Wand"));
        meta.lore(List.of(
            Component.text("\u00a77Rechtsklick: Punkt setzen"),
            Component.text("\u00a77Max. 4 Punkte \u2192 /border create")
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    private Component prefix() {
        return Component.text("\u00a7d[Border] \u00a7r", TextColor.color(0xFF69B4));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("wand", "create", "remove", "clear", "info");
        return List.of();
    }
}
