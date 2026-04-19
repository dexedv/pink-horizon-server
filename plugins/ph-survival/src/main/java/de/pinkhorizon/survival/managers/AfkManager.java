package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AfkManager {

    private static final long AFK_TIMEOUT_MS = 5 * 60 * 1000L; // 5 Minuten

    private final PHSurvival plugin;
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, Boolean> afkStatus = new HashMap<>();

    public AfkManager(PHSurvival plugin) {
        this.plugin = plugin;
        // Jede 30 Sekunden AFK prüfen
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAll, 600L, 600L);
    }

    public void resetAfk(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        if (Boolean.TRUE.equals(afkStatus.get(uuid))) {
            afkStatus.put(uuid, false);
            plugin.getServer().broadcastMessage("§e" + player.getName() + " §7ist nicht mehr AFK.");
            plugin.getTabManager().update(player);
            plugin.getRankManager().applyTabName(player);
        }
    }

    public void setJoined(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        afkStatus.put(player.getUniqueId(), false);
    }

    public void remove(Player player) {
        lastActivity.remove(player.getUniqueId());
        afkStatus.remove(player.getUniqueId());
    }

    public boolean isAfk(UUID uuid) {
        return Boolean.TRUE.equals(afkStatus.get(uuid));
    }

    private void checkAll() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long last = lastActivity.getOrDefault(uuid, now);
            boolean currentlyAfk = Boolean.TRUE.equals(afkStatus.get(uuid));
            if (!currentlyAfk && now - last > AFK_TIMEOUT_MS) {
                afkStatus.put(uuid, true);
                plugin.getServer().broadcastMessage("§e" + player.getName() + " §7ist jetzt AFK.");
                plugin.getTabManager().update(player);
                plugin.getRankManager().applyTabName(player);
            }
        }
    }
}
