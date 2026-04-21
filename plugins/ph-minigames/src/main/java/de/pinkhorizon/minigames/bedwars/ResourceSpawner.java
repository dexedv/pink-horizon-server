package de.pinkhorizon.minigames.bedwars;

import de.pinkhorizon.minigames.PHMinigames;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawnt Ressourcen-Items an konfigurierten Positionen.
 * Iron: alle 3s, Gold: alle 8s, Diamond: alle 60s, Emerald: alle 120s.
 */
public class ResourceSpawner {

    public enum Type {
        IRON    (Material.IRON_INGOT,    60L,   "§7Eisen"),
        GOLD    (Material.GOLD_INGOT,    160L,  "§6Gold"),
        DIAMOND (Material.DIAMOND,       1200L, "§bDiamant"),
        EMERALD (Material.EMERALD,       2400L, "§aEmerald");

        public final Material material;
        public final long     intervalTicks;
        public final String   displayName;

        Type(Material material, long intervalTicks, String displayName) {
            this.material      = material;
            this.intervalTicks = intervalTicks;
            this.displayName   = displayName;
        }

        public static Type fromString(String s) {
            for (Type t : values()) if (t.name().equalsIgnoreCase(s)) return t;
            return IRON;
        }
    }

    private final PHMinigames            plugin;
    private final List<BukkitTask>       tasks = new ArrayList<>();
    private final List<SpawnerInstance>  instances;

    public ResourceSpawner(PHMinigames plugin, BedWarsArenaConfig config, World world) {
        this.plugin    = plugin;
        this.instances = new ArrayList<>();

        for (BedWarsArenaConfig.SpawnerEntry entry : config.spawners) {
            Type     type = Type.fromString(entry.type());
            Location loc  = new Location(world, entry.x(), entry.y(), entry.z());
            instances.add(new SpawnerInstance(type, loc));
        }
    }

    public void start() {
        for (SpawnerInstance inst : instances) {
            BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                World w = inst.location.getWorld();
                if (w == null) return;
                ItemStack item = new ItemStack(inst.type.material);
                Item dropped = w.dropItem(inst.location, item);
                dropped.setVelocity(new Vector(0, 0.1, 0));
                dropped.setPickupDelay(0);
            }, inst.type.intervalTicks, inst.type.intervalTicks);
            tasks.add(task);
        }
    }

    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private record SpawnerInstance(Type type, Location location) {}
}
