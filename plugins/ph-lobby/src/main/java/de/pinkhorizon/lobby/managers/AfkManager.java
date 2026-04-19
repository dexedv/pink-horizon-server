package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AfkManager implements Listener {

    private final PHLobby plugin;
    private final Map<UUID, Long> lastAction = new HashMap<>();
    private final Set<UUID> afkPlayers    = new HashSet<>();
    private BukkitTask checkTask;

    // 5 Minuten = AFK-Markierung, 10 Minuten = Kick
    private static final long AFK_MS  = 5  * 60 * 1000L;
    private static final long KICK_MS = 10 * 60 * 1000L;

    public AfkManager(PHLobby plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startCheckTask();
    }

    private void startCheckTask() {
        // Alle 30 Sekunden prüfen
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("lobby.admin")) continue;
                long idle = now - lastAction.getOrDefault(player.getUniqueId(), now);

                if (idle >= KICK_MS) {
                    player.kick(Component.text(
                        "Du wurdest wegen Inaktivität vom Server getrennt.", NamedTextColor.RED));
                } else if (idle >= AFK_MS && !afkPlayers.contains(player.getUniqueId())) {
                    afkPlayers.add(player.getUniqueId());
                    player.sendMessage(Component.text(
                        "§7Du bist jetzt AFK. Bewege dich um den Status zu entfernen."));
                    Bukkit.broadcast(Component.text(
                        "§8[§7AFK§8] §7" + player.getName() + " ist jetzt inaktiv."));
                }
            }
        }, 600L, 600L);
    }

    public void resetAction(Player player) {
        boolean wasAfk = afkPlayers.remove(player.getUniqueId());
        lastAction.put(player.getUniqueId(), System.currentTimeMillis());
        if (wasAfk) {
            Bukkit.broadcast(Component.text(
                "§8[§a✔§8] §7" + player.getName() + " ist nicht mehr AFK."));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        resetAction(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        resetAction(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastAction.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastAction.remove(event.getPlayer().getUniqueId());
        afkPlayers.remove(event.getPlayer().getUniqueId());
    }

    public boolean isAfk(UUID uuid) { return afkPlayers.contains(uuid); }

    public void stop() {
        if (checkTask != null) checkTask.cancel();
    }
}
