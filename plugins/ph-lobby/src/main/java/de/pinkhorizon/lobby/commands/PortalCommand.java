package de.pinkhorizon.lobby.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class PortalCommand implements CommandExecutor, TabCompleter, Listener {

    private final PHLobby plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, List<Location>> selections = new HashMap<>();
    private final Map<String, Portal> portals = new HashMap<>();
    private final Set<UUID> cooldowns = new HashSet<>();
    private BukkitTask particleTask;
    private final Random random = new Random();
    private int tick = 0;

    private static class Portal {
        final String name, server, world;
        final double minX, maxX, minY, maxY, minZ, maxZ;

        Portal(String name, String server, String world,
               double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
            this.name = name; this.server = server; this.world = world;
            this.minX = minX; this.maxX = maxX;
            this.minY = minY; this.maxY = maxY;
            this.minZ = minZ; this.maxZ = maxZ;
        }

        boolean contains(Location loc) {
            if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
            return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
        }

        Location center() {
            World w = Bukkit.getWorld(world);
            return new Location(w,
                (minX + maxX) / 2.0,
                (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0);
        }
    }

    public PortalCommand(PHLobby plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "portal_wand");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadPortals();
        startParticleTask();
    }

    // ── Config laden/speichern ──────────────────────────────────────────

    private void loadPortals() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("portals");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            ConfigurationSection p = sec.getConfigurationSection(name);
            if (p == null) continue;
            portals.put(name, new Portal(name, p.getString("server", name), p.getString("world", "world"),
                p.getDouble("minX"), p.getDouble("maxX"),
                p.getDouble("minY"), p.getDouble("maxY"),
                p.getDouble("minZ"), p.getDouble("maxZ")));
        }
        plugin.getLogger().info(portals.size() + " Portal(e) geladen.");
    }

    private void savePortal(Portal portal) {
        String path = "portals." + portal.name;
        plugin.getConfig().set(path + ".server", portal.server);
        plugin.getConfig().set(path + ".world",  portal.world);
        plugin.getConfig().set(path + ".minX",   portal.minX);
        plugin.getConfig().set(path + ".maxX",   portal.maxX);
        plugin.getConfig().set(path + ".minY",   portal.minY);
        plugin.getConfig().set(path + ".maxY",   portal.maxY);
        plugin.getConfig().set(path + ".minZ",   portal.minZ);
        plugin.getConfig().set(path + ".maxZ",   portal.maxZ);
        plugin.saveConfig();
    }

    // ── Partikel-Task ───────────────────────────────────────────────────

    private Particle.DustOptions getDustColor(String server) {
        Color color = switch (server.toLowerCase()) {
            case "survival"  -> Color.fromRGB(0x55FF55);
            case "skyblock"  -> Color.fromRGB(0xFFD700);
            case "minigames" -> Color.fromRGB(0xFF4500);
            default          -> Color.fromRGB(0xFF69B4);
        };
        return new Particle.DustOptions(color, 1.5f);
    }

    private void startParticleTask() {
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick++;
            for (Portal portal : portals.values()) {
                World world = Bukkit.getWorld(portal.world);
                if (world == null) continue;

                Particle.DustOptions dust = getDustColor(portal.server);
                double cx  = (portal.minX + portal.maxX) / 2.0;
                double cz  = (portal.minZ + portal.maxZ) / 2.0;
                double dx  = portal.maxX - portal.minX;
                double dy  = portal.maxY - portal.minY;
                double dz  = portal.maxZ - portal.minZ;
                double floorY = portal.minY + 0.1;

                // 1. Farbiger Bodenrand
                double step = 0.4;
                for (double x = portal.minX; x <= portal.maxX; x += step) {
                    world.spawnParticle(Particle.DUST, x, floorY, portal.minZ, 1, 0, 0, 0, 0, dust);
                    world.spawnParticle(Particle.DUST, x, floorY, portal.maxZ, 1, 0, 0, 0, 0, dust);
                }
                for (double z = portal.minZ; z <= portal.maxZ; z += step) {
                    world.spawnParticle(Particle.DUST, portal.minX, floorY, z, 1, 0, 0, 0, 0, dust);
                    world.spawnParticle(Particle.DUST, portal.maxX, floorY, z, 1, 0, 0, 0, 0, dust);
                }

                // 2. Portal-Partikel innen
                for (int i = 0; i < 10; i++) {
                    double x = portal.minX + random.nextDouble() * dx;
                    double y = portal.minY + random.nextDouble() * dy;
                    double z = portal.minZ + random.nextDouble() * dz;
                    world.spawnParticle(Particle.PORTAL, x, y, z, 2, 0, 0, 0, 0.05);
                }

                // 3. Aufsteigende Funkeln (END_ROD)
                for (int i = 0; i < 4; i++) {
                    double x = portal.minX + random.nextDouble() * dx;
                    double z = portal.minZ + random.nextDouble() * dz;
                    world.spawnParticle(Particle.END_ROD, x, floorY, z, 1, 0, 0.04, 0, 0.01);
                }

                // 4. Rotierende Aura um Mittelpunkt
                double angle  = tick * (18.0 * Math.PI / 180.0);
                double radius = Math.min(dx, dz) / 2.0 * 0.75;
                int    points = 8;
                for (int i = 0; i < points; i++) {
                    double a  = angle + (2.0 * Math.PI * i / points);
                    double rx = cx + Math.cos(a) * radius;
                    double rz = cz + Math.sin(a) * radius;
                    world.spawnParticle(Particle.DUST, rx, floorY, rz, 1, 0, 0, 0, 0, dust);
                }

                // 5. Pulsierender Kern (jede 2. Runde)
                if (tick % 2 == 0) {
                    world.spawnParticle(Particle.END_ROD, cx, floorY, cz, 3, 0.2, 0.05, 0.2, 0.005);
                }
            }
        }, 0L, 10L);
    }

    public void stopParticleTask() {
        if (particleTask != null) particleTask.cancel();
    }

    // ── Befehl ──────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }
        if (!player.hasPermission("lobby.admin")) {
            player.sendMessage("§cKeine Berechtigung!");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "help";

        switch (sub) {
            case "wand" -> {
                player.getInventory().addItem(buildWand());
                player.sendMessage(prefix().append(Component.text(
                    "Portal-Wand erhalten! Rechtsklick = Punkt setzen.", NamedTextColor.GREEN)));
            }
            case "clear" -> {
                selections.remove(player.getUniqueId());
                player.sendMessage(prefix().append(Component.text("Auswahl geleert.", NamedTextColor.YELLOW)));
            }
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(prefix().append(Component.text(
                        "Nutzung: /portal create <name> <server>", NamedTextColor.RED)));
                    return true;
                }
                createPortal(player, args[1].toLowerCase(), args[2].toLowerCase());
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(prefix().append(Component.text(
                        "Nutzung: /portal remove <name>", NamedTextColor.RED)));
                    return true;
                }
                removePortal(player, args[1].toLowerCase());
            }
            case "list" -> listPortals(player);
            default -> player.sendMessage(prefix().append(Component.text(
                "/portal <wand | create <name> <server> | remove <name> | clear | list>",
                NamedTextColor.GRAY)));
        }
        return true;
    }

    // ── Wand-Klick ──────────────────────────────────────────────────────

    @EventHandler
    public void onWandClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.ENDER_EYE || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        event.setCancelled(true);

        Location loc = player.getLocation();
        List<Location> points = selections.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());

        if (points.size() >= 4) {
            player.sendMessage(prefix().append(Component.text(
                "Bereits 4 Punkte. /portal create <name> <server> oder /portal clear",
                NamedTextColor.RED)));
            return;
        }

        points.add(loc.clone());
        int num = points.size();

        player.sendMessage(prefix().append(Component.text(
            "Punkt " + num + " gesetzt: X=" + (int) loc.getX()
            + " Y=" + (int) loc.getY() + " Z=" + (int) loc.getZ(),
            NamedTextColor.GREEN)));

        player.sendActionBar(Component.text(
            "§5" + num + "/4 Punkte gesetzt"
            + (num == 4 ? " §a✔ /portal create <name> <server>" : ""),
            NamedTextColor.LIGHT_PURPLE));

        player.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 25, 0.3, 0.3, 0.3, 0.1);
    }

    // ── Portal erstellen ────────────────────────────────────────────────

    private void createPortal(Player player, String name, String server) {
        List<Location> points = selections.get(player.getUniqueId());

        if (points == null || points.size() < 2) {
            player.sendMessage(prefix().append(Component.text(
                "Mindestens 2 Punkte mit dem Wand markieren! (/portal wand)", NamedTextColor.RED)));
            return;
        }

        double minX = points.stream().mapToDouble(Location::getX).min().getAsDouble();
        double maxX = points.stream().mapToDouble(Location::getX).max().getAsDouble();
        double minY = points.stream().mapToDouble(Location::getY).min().getAsDouble();
        double maxY = points.stream().mapToDouble(Location::getY).max().getAsDouble() + 2.0;
        double minZ = points.stream().mapToDouble(Location::getZ).min().getAsDouble();
        double maxZ = points.stream().mapToDouble(Location::getZ).max().getAsDouble();

        Portal portal = new Portal(name, server, player.getWorld().getName(),
            minX, maxX, minY, maxY, minZ, maxZ);
        portals.put(name, portal);
        savePortal(portal);
        selections.remove(player.getUniqueId());

        player.sendMessage(prefix().append(Component.text(
            "Portal §f" + name + " §5→ §f" + server + " §aerstellt!", NamedTextColor.GREEN)));
        player.sendMessage(Component.text(
            "  Bereich: X[" + (int) minX + " bis " + (int) maxX + "]"
            + " Z[" + (int) minZ + " bis " + (int) maxZ + "]", NamedTextColor.GRAY));
    }

    // ── Portal entfernen ────────────────────────────────────────────────

    private void removePortal(Player player, String name) {
        if (portals.remove(name) == null) {
            player.sendMessage(prefix().append(Component.text(
                "Portal '" + name + "' nicht gefunden.", NamedTextColor.RED)));
            return;
        }
        plugin.getConfig().set("portals." + name, null);
        plugin.saveConfig();
        player.sendMessage(prefix().append(Component.text("Portal '" + name + "' entfernt.", NamedTextColor.YELLOW)));
    }

    // ── Portal-Liste ────────────────────────────────────────────────────

    private void listPortals(Player player) {
        player.sendMessage(prefix().append(Component.text(
            "=== Portale (" + portals.size() + ") ===", TextColor.color(0xFF69B4))));
        if (portals.isEmpty()) {
            player.sendMessage(Component.text("  Keine Portale vorhanden.", NamedTextColor.GRAY));
            return;
        }
        portals.values().forEach(p -> player.sendMessage(Component.text(
            "  " + p.name + " → " + p.server, NamedTextColor.WHITE)));
    }

    // ── Spieler betritt Portal ──────────────────────────────────────────

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Nur prüfen wenn Spieler Block wechselt (Performance)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        if (cooldowns.contains(player.getUniqueId())) return;

        for (Portal portal : portals.values()) {
            if (!portal.contains(event.getTo())) continue;

            // Cooldown setzen (3 Sekunden)
            cooldowns.add(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin,
                () -> cooldowns.remove(player.getUniqueId()), 60L);

            player.sendMessage(prefix().append(Component.text(
                "Verbinde mit §f" + portal.server + "§5...", NamedTextColor.LIGHT_PURPLE)));
            player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.2f);
            player.getWorld().spawnParticle(Particle.PORTAL,
                portal.center(), 80, 1.0, 1.0, 1.0, 0.3);

            @SuppressWarnings("UnstableApiUsage")
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(portal.server);
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
            break;
        }
    }

    // ── Hilfsmethoden ───────────────────────────────────────────────────

    private ItemStack buildWand() {
        ItemStack wand = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text("§5§lPortal-Wand"));
        meta.lore(List.of(
            Component.text("§7Rechtsklick: Punkt setzen"),
            Component.text("§7Max. 4 Punkte → /portal create <name> <server>")
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    private Component prefix() {
        return Component.text("§5[Portal] §r", TextColor.color(0xFF69B4));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("wand", "create", "remove", "clear", "list");
        if (args.length == 2 && args[0].equalsIgnoreCase("remove"))
            return new ArrayList<>(portals.keySet());
        if (args.length == 3 && args[0].equalsIgnoreCase("create"))
            return List.of("survival", "skyblock", "minigames");
        return List.of();
    }
}
