package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SpawnBorderManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class SpawnBorderCommand implements CommandExecutor, TabCompleter {

    public static final NamespacedKey WAND_KEY = new NamespacedKey("ph-survival", "spawn_border_wand");

    private final PHSurvival plugin;
    private org.bukkit.scheduler.BukkitTask permanentTask = null;

    public SpawnBorderCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur Spieler!"); return true; }
        if (!player.hasPermission("survival.admin")) {
            player.sendMessage(Component.text("§cKein Zugriff!"));
            return true;
        }

        SpawnBorderManager bm = plugin.getSpawnBorderManager();

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "wand" -> {
                player.getInventory().addItem(makeWand());
                player.sendMessage(Component.text("§aSpawn-Border Wand erhalten! §7Rechtsklick auf Block = Punkt setzen."));
            }
            case "add" -> {
                int num = bm.addPoint(player.getLocation().getX(), player.getLocation().getZ());
                player.sendMessage(Component.text(
                    "§aPunkt §e#" + num + " §agesetzt: §7X=" + fmt(player.getLocation().getX())
                    + " Z=" + fmt(player.getLocation().getZ())
                    + " §8(Gesamt: " + bm.getPoints().size() + ")"));
                showParticlesAround(player, player.getLocation().getX(), player.getLocation().getZ());
            }
            case "remove" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /spawnborder remove <Index>")); return true; }
                try {
                    int idx = Integer.parseInt(args[1]);
                    if (bm.removePoint(idx)) {
                        player.sendMessage(Component.text("§7Punkt §e#" + idx + " §7entfernt."));
                    } else {
                        player.sendMessage(Component.text("§cIndex außerhalb des Bereichs! (1-" + bm.getPoints().size() + ")"));
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("§cKein gültiger Index!"));
                }
            }
            case "list" -> {
                var pts = bm.getPoints();
                if (pts.isEmpty()) { player.sendMessage(Component.text("§7Keine Punkte gesetzt.")); return true; }
                player.sendMessage(Component.text("§6§l── Border-Punkte (" + pts.size() + ") ──"));
                for (int i = 0; i < pts.size(); i++) {
                    var p = pts.get(i);
                    player.sendMessage(Component.text("§e #" + (i+1) + " §7X=" + fmt(p.x()) + " Z=" + fmt(p.z())));
                }
                if (!bm.hasPolygon()) {
                    player.sendMessage(Component.text("§c⚠ Mindestens 3 Punkte nötig für einen aktiven Border!"));
                }
            }
            case "clear" -> {
                bm.clearPoints();
                player.sendMessage(Component.text("§7Alle Border-Punkte gelöscht."));
            }
            case "show" -> {
                if (!bm.hasPolygon()) {
                    player.sendMessage(Component.text("§cNoch kein Polygon definiert (mind. 3 Punkte)."));
                    return true;
                }
                player.sendMessage(Component.text("§aZeige Border für 15 Sekunden..."));
                showBorderParticles(player, 15);
            }
            case "toggle" -> {
                if (!bm.hasPolygon()) {
                    player.sendMessage(Component.text("§cNoch kein Polygon definiert (mind. 3 Punkte)."));
                    return true;
                }
                if (permanentTask != null) {
                    permanentTask.cancel();
                    permanentTask = null;
                    player.sendMessage(Component.text("§7Border-Anzeige §cdeaktiviert§7."));
                } else {
                    permanentTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                        var pts = plugin.getSpawnBorderManager().getPoints();
                        if (pts.size() < 3) return;
                        int n = pts.size();
                        for (org.bukkit.entity.Player online : plugin.getServer().getOnlinePlayers()) {
                            if (!online.hasPermission("survival.admin")) continue;
                            double y = online.getLocation().getY() + 1.5;
                            for (int i = 0, j = n - 1; i < n; j = i++) {
                                double ax = pts.get(j).x(), az = pts.get(j).z();
                                double bx = pts.get(i).x(), bz = pts.get(i).z();
                                double dist = Math.sqrt((bx-ax)*(bx-ax) + (bz-az)*(bz-az));
                                int steps = Math.max(1, (int)(dist / 0.8));
                                for (int s = 0; s <= steps; s++) {
                                    double t = (double) s / steps;
                                    double px = ax + t * (bx - ax);
                                    double pz = az + t * (bz - az);
                                    online.spawnParticle(Particle.FLAME, px, y, pz, 1, 0, 0, 0, 0);
                                }
                            }
                        }
                    }, 0L, 5L);
                    player.sendMessage(Component.text("§aBorder-Anzeige §adauerhaft aktiviert§a. §7(/spawnborder toggle zum Deaktivieren)"));
                }
            }
            case "tp" -> {
                if (args.length < 2) { player.sendMessage(Component.text("§cNutzung: /spawnborder tp <Index>")); return true; }
                try {
                    int idx = Integer.parseInt(args[1]) - 1;
                    var pts = bm.getPoints();
                    if (idx < 0 || idx >= pts.size()) { player.sendMessage(Component.text("§cIndex außerhalb des Bereichs!")); return true; }
                    var pt = pts.get(idx);
                    player.teleport(new Location(player.getWorld(), pt.x(), player.getLocation().getY(), pt.z(), player.getYaw(), player.getPitch()));
                    player.sendMessage(Component.text("§aTeleportiert zu Punkt §e#" + (idx+1)));
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("§cKein gültiger Index!"));
                }
            }
            default -> sendHelp(player);
        }
        return true;
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────

    public static ItemStack makeWand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§6§lSpawn-Border Wand"));
        List<Component> lore = List.of(
            Component.text("§7Rechtsklick auf Block §e→ Punkt setzen"),
            Component.text("§7Linksklick §e→ letzten Punkt entfernen"),
            Component.text("§8/spawnborder list §7zum Anzeigen")
        );
        meta.lore(lore);
        meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
    }

    /** Zeigt für `seconds` Sekunden Partikel entlang aller Border-Kanten. */
    public void showBorderParticles(Player player, int seconds) {
        var pts = plugin.getSpawnBorderManager().getPoints();
        int ticks = seconds * 20;
        int[] elapsed = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            elapsed[0] += 5;
            if (elapsed[0] > ticks) { task.cancel(); return; }
            double y = player.getLocation().getY() + 1.5;
            int n = pts.size();
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double ax = pts.get(j).x(), az = pts.get(j).z();
                double bx = pts.get(i).x(), bz = pts.get(i).z();
                double dist = Math.sqrt((bx-ax)*(bx-ax) + (bz-az)*(bz-az));
                int steps = Math.max(1, (int)(dist / 0.8));
                for (int s = 0; s <= steps; s++) {
                    double t = (double) s / steps;
                    double px = ax + t * (bx - ax);
                    double pz = az + t * (bz - az);
                    player.spawnParticle(Particle.FLAME, px, y, pz, 1, 0, 0, 0, 0);
                }
            }
        }, 0L, 5L);
    }

    private void showParticlesAround(Player player, double x, double z) {
        double y = player.getLocation().getY() + 1;
        for (int a = 0; a < 360; a += 20) {
            double rad = Math.toRadians(a);
            player.spawnParticle(Particle.FLAME, x + Math.cos(rad), y, z + Math.sin(rad), 1, 0, 0, 0, 0);
        }
    }

    private String fmt(double v) {
        return String.format("%.1f", v);
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6§l── Spawn-Border ──"));
        player.sendMessage(Component.text("§e/spawnborder wand §7- Wand erhalten (Rechtsklick = Punkt)"));
        player.sendMessage(Component.text("§e/spawnborder add §7- Punkt an aktueller Position"));
        player.sendMessage(Component.text("§e/spawnborder remove <#> §7- Punkt entfernen"));
        player.sendMessage(Component.text("§e/spawnborder list §7- Alle Punkte anzeigen"));
        player.sendMessage(Component.text("§e/spawnborder clear §7- Alle Punkte löschen"));
        player.sendMessage(Component.text("§e/spawnborder show §7- Border 15s sichtbar machen"));
        player.sendMessage(Component.text("§e/spawnborder toggle §7- Border dauerhaft ein/ausblenden"));
        player.sendMessage(Component.text("§e/spawnborder tp <#> §7- Zu Punkt teleportieren"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("wand", "add", "remove", "list", "clear", "show", "toggle", "tp");
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("tp"))) {
            var pts = plugin.getSpawnBorderManager().getPoints();
            return java.util.stream.IntStream.rangeClosed(1, pts.size())
                .mapToObj(Integer::toString).toList();
        }
        return List.of();
    }
}
