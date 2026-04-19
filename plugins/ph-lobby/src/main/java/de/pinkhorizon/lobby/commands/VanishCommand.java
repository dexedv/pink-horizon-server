package de.pinkhorizon.lobby.commands;

import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishCommand implements CommandExecutor, Listener {

    private final PHLobby plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishCommand(PHLobby plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

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

        if (vanished.contains(player.getUniqueId())) {
            unvanish(player);
        } else {
            vanish(player);
        }
        return true;
    }

    private void vanish(Player player) {
        vanished.add(player.getUniqueId());

        // Vor allen Nicht-Admins verstecken
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.hasPermission("lobby.admin")) {
                other.hidePlayer(plugin, player);
            }
        }

        // Fake-Quit-Nachricht senden
        Bukkit.broadcast(
            Component.text("§8[§c-§8] ", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
        );

        player.sendMessage(Component.text("§8[Vanish] §aUnsichtbar aktiviert. Nur Admins sehen dich."));
    }

    private void unvanish(Player player) {
        vanished.remove(player.getUniqueId());

        // Für alle Spieler wieder sichtbar machen
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }

        // Fake-Join-Nachricht senden
        Bukkit.broadcast(
            Component.text("§8[§a+§8] ", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
        );

        player.sendMessage(Component.text("§8[Vanish] §cUnsichtbar deaktiviert."));
    }

    // Beim Join: vanished Spieler vor dem Neuen verstecken
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();

        // Versteckte Admins vor neuem Spieler (ohne Admin) verbergen
        for (UUID uid : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(uid);
            if (vanishedPlayer == null) continue;
            if (!joined.hasPermission("lobby.admin")) {
                joined.hidePlayer(plugin, vanishedPlayer);
            }
        }

        // Wenn der Beitretende selbst vanished ist (z.B. nach Reconnect), verstecken
        if (vanished.contains(joined.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission("lobby.admin") && !other.equals(joined)) {
                    other.hidePlayer(plugin, joined);
                }
            }
        }
    }

    public boolean isVanished(UUID uuid) { return vanished.contains(uuid); }
}
