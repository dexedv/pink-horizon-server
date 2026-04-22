package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class AfkManager {

    private static final long AFK_TIMEOUT_MS  =  5 * 60 * 1000L; // 5 Minuten  → AFK-Meldung
    private static final long KICK_TIMEOUT_MS = 30 * 60 * 1000L; // 30 Minuten → Kick

    // Option 1 – AFK-Pool: letzte 20 Block-Positionen, < 6 einzigartige = Wiederholung
    private static final int  HISTORY_SIZE = 20;
    private static final int  MIN_UNIQUE   = 6;

    private final PHSurvival plugin;
    private final Map<UUID, Long>          lastActivity = new HashMap<>();
    private final Map<UUID, Boolean>       afkStatus    = new HashMap<>();
    private final Map<UUID, ArrayDeque<Long>> posHistory = new HashMap<>();

    public AfkManager(PHSurvival plugin) {
        this.plugin = plugin;
        // Jede 30 Sekunden AFK prüfen
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAll, 600L, 600L);
    }

    /**
     * Option 1: Wird bei Blockwechsel aufgerufen.
     * Akkumuliert Positions-History und erkennt AFK-Pools (Bewegungswiederholung).
     * Normales Laufen aktualisiert nur den AFK-Timer, löscht aber die History NICHT.
     */
    public void onPlayerMove(Player player, int bx, int bz) {
        UUID uuid = player.getUniqueId();
        long packed = packPos(bx, bz);

        ArrayDeque<Long> hist = posHistory.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        hist.addLast(packed);
        if (hist.size() > HISTORY_SIZE) hist.removeFirst();

        // Erst prüfen wenn History voll ist
        if (hist.size() >= HISTORY_SIZE) {
            long unique = new HashSet<>(hist).size();
            if (unique < MIN_UNIQUE) {
                // AFK-Pool erkannt → sofort AFK markieren
                if (!Boolean.TRUE.equals(afkStatus.get(uuid))) {
                    afkStatus.put(uuid, true);
                    plugin.getServer().broadcastMessage(
                        "§e" + player.getName() + " §7ist jetzt AFK. §8(Bewegungsmuster erkannt)");
                    plugin.getTabManager().update(player);
                    plugin.getRankManager().applyTabName(player);
                }
                return; // AFK-Timer NICHT zurücksetzen
            }
        }

        // Normale Bewegung: nur Timer aktualisieren, History NICHT löschen
        lastActivity.put(uuid, System.currentTimeMillis());
        if (Boolean.TRUE.equals(afkStatus.get(uuid))) {
            afkStatus.put(uuid, false);
            plugin.getServer().broadcastMessage("§e" + player.getName() + " §7ist nicht mehr AFK.");
            plugin.getTabManager().update(player);
            plugin.getRankManager().applyTabName(player);
        }
    }

    /** Option 4: Echte Spieleraktionen (Block abbauen/setzen, Chat, Kampf) – setzt alles zurück. */
    public void resetAfk(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        posHistory.remove(uuid); // History leeren bei echter Aktion
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
        UUID uuid = player.getUniqueId();
        lastActivity.remove(uuid);
        afkStatus.remove(uuid);
        posHistory.remove(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return Boolean.TRUE.equals(afkStatus.get(uuid));
    }

    private void checkAll() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long last = lastActivity.getOrDefault(uuid, now);
            long idle = now - last;
            boolean currentlyAfk = Boolean.TRUE.equals(afkStatus.get(uuid));

            if (currentlyAfk && idle > KICK_TIMEOUT_MS && !player.isOp()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.kick(net.kyori.adventure.text.Component.text(
                        "§cDu wurdest wegen Inaktivität gekickt.\n§7Bitte melde dich wieder, wenn du aktiv bist!")));
                continue;
            }

            if (!currentlyAfk && idle > AFK_TIMEOUT_MS) {
                afkStatus.put(uuid, true);
                plugin.getServer().broadcastMessage("§e" + player.getName() + " §7ist jetzt AFK.");
                plugin.getTabManager().update(player);
                plugin.getRankManager().applyTabName(player);
            }
        }
    }

    private static long packPos(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
