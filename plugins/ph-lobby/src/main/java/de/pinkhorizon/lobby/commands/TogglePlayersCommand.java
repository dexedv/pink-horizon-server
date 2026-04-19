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
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TogglePlayersCommand implements CommandExecutor, Listener {

    private final PHLobby plugin;
    private final Set<UUID> hiding = new HashSet<>();

    public TogglePlayersCommand(PHLobby plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (hiding.contains(player.getUniqueId())) {
            showAll(player);
        } else {
            hideAll(player);
        }
        return true;
    }

    private void hideAll(Player player) {
        hiding.add(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            player.hidePlayer(plugin, other);
        }
        player.sendMessage(Component.text(
            "§8[§c✖§8] §7Spieler ausgeblendet. /toggleplayers zum Einblenden.", NamedTextColor.GRAY));
    }

    private void showAll(Player player) {
        hiding.remove(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            // Vanished Admins nur für Admins wieder zeigen
            if (plugin.getVanishCommand().isVanished(other.getUniqueId())
                && !player.hasPermission("lobby.admin")) continue;
            player.showPlayer(plugin, other);
        }
        player.sendMessage(Component.text(
            "§8[§a✔§8] §7Spieler eingeblendet.", NamedTextColor.GRAY));
    }

    // Neuer Spieler joint → bei allen die Spieler ausgeblendet haben verstecken
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID uid : hiding) {
            Player hider = Bukkit.getPlayer(uid);
            if (hider == null || hider.equals(joined)) continue;
            hider.hidePlayer(plugin, joined);
        }
    }

    // Spieler verlässt → bei allen wieder einblenden die ihn ausgeblendet hatten
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hiding.remove(event.getPlayer().getUniqueId());
    }

    public boolean isHiding(UUID uuid) { return hiding.contains(uuid); }
}
