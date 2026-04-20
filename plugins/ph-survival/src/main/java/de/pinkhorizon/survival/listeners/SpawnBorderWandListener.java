package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.commands.SpawnBorderCommand;
import de.pinkhorizon.survival.managers.SpawnBorderManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SpawnBorderWandListener implements Listener {

    private final PHSurvival plugin;

    public SpawnBorderWandListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("survival.admin")) return;

        var item = event.getItem();
        if (!SpawnBorderCommand.isWand(item)) return;

        event.setCancelled(true); // Wand soll nichts in der Welt tun

        SpawnBorderManager bm = plugin.getSpawnBorderManager();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            // Punkt an der Mitte des geklickten Blocks setzen
            double x = event.getClickedBlock().getX() + 0.5;
            double z = event.getClickedBlock().getZ() + 0.5;
            int num = bm.addPoint(x, z);
            player.sendMessage(Component.text(
                "§a[Border] Punkt §e#" + num + " §agesetzt: §7X=" + String.format("%.1f", x)
                + " Z=" + String.format("%.1f", z)
                + " §8(" + bm.getPoints().size() + " Punkte gesamt)"));
            spawnPointParticle(player, x, z);
            if (bm.hasPolygon()) showEdgeParticle(player, num - 1);

        } else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            // Punkt an Spielerposition setzen
            double x = player.getLocation().getX();
            double z = player.getLocation().getZ();
            int num = bm.addPoint(x, z);
            player.sendMessage(Component.text(
                "§a[Border] Punkt §e#" + num + " §agesetzt: §7X=" + String.format("%.1f", x)
                + " Z=" + String.format("%.1f", z)
                + " §8(" + bm.getPoints().size() + " Punkte gesamt)"));
            spawnPointParticle(player, x, z);

        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK
                || event.getAction() == Action.LEFT_CLICK_AIR) {
            // Letzten Punkt entfernen
            int size = bm.getPoints().size();
            if (size == 0) { player.sendMessage(Component.text("§7Keine Punkte zum Entfernen.")); return; }
            bm.removePoint(size);
            player.sendMessage(Component.text("§7[Border] Letzter Punkt §e#" + size + " §7entfernt. §8(Noch: " + bm.getPoints().size() + ")"));
        }
    }

    private void spawnPointParticle(Player player, double x, double z) {
        double y = player.getWorld().getHighestBlockYAt((int) x, (int) z) + 1.5;
        for (int a = 0; a < 360; a += 15) {
            double rad = Math.toRadians(a);
            player.spawnParticle(Particle.FLAME, x + Math.cos(rad) * 0.5, y, z + Math.sin(rad) * 0.5, 1, 0, 0, 0, 0);
        }
    }

    /** Zeigt die neue Kante zwischen dem letzten und vorletzten Punkt. */
    private void showEdgeParticle(Player player, int newIndex) {
        var pts = plugin.getSpawnBorderManager().getPoints();
        if (pts.size() < 2) return;
        int prevIndex = (newIndex - 1 + pts.size()) % pts.size();
        var a = pts.get(prevIndex);
        var b = pts.get(newIndex);
        double y = player.getLocation().getY() + 1.5;
        double dist = Math.sqrt(Math.pow(b.x()-a.x(),2) + Math.pow(b.z()-a.z(),2));
        int steps = Math.max(1, (int)(dist / 0.6));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            double px = a.x() + t * (b.x() - a.x());
            double pz = a.z() + t * (b.z() - a.z());
            player.spawnParticle(Particle.FLAME, px, y, pz, 1, 0, 0, 0, 0);
        }
    }
}
