package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

public class ItemClearManager {

    private static final long INTERVAL_TICKS = 144_000L; // 2 Stunden (2 * 60 * 60 * 20)
    private static final long WARN_TICKS      =   6_000L; // 5 Minuten vor dem Clear

    private final PHSurvival plugin;
    private BukkitTask warnTask;
    private BukkitTask clearTask;

    public ItemClearManager(PHSurvival plugin) {
        this.plugin = plugin;
        schedule();
    }

    private void schedule() {
        warnTask  = plugin.getServer().getScheduler()
            .runTaskLater(plugin, this::broadcastWarning, INTERVAL_TICKS - WARN_TICKS);
        clearTask = plugin.getServer().getScheduler()
            .runTaskLater(plugin, () -> executeClear(null), INTERVAL_TICKS);
    }

    public void broadcastWarning() {
        broadcast("§6§l[!] §eIn §c5 Minuten §ewerden alle Items auf dem Boden gelöscht!");
    }

    /**
     * Führt den Item-Clear aus. Bricht laufende geplante Tasks ab und plant neue.
     * @return Anzahl gelöschter Items
     */
    public int executeClear(org.bukkit.command.CommandSender trigger) {
        if (warnTask  != null) { warnTask.cancel();  warnTask  = null; }
        if (clearTask != null) { clearTask.cancel(); clearTask = null; }

        int count = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof Item) {
                    e.remove();
                    count++;
                }
            }
        }

        String msg = "§c§l[Item-Clear] §eAlle Items auf dem Boden wurden gelöscht! §8(§7" + count + " Items§8)";
        broadcast(msg);
        if (trigger != null) {
            trigger.sendMessage("§aItem-Clear ausgeführt: §7" + count + " Items entfernt.");
        }

        schedule();
        return count;
    }

    private void broadcast(String legacy) {
        plugin.getServer().broadcast(
            LegacyComponentSerializer.legacySection().deserialize(legacy)
        );
    }

    public void cancel() {
        if (warnTask  != null) warnTask.cancel();
        if (clearTask != null) clearTask.cancel();
    }
}
