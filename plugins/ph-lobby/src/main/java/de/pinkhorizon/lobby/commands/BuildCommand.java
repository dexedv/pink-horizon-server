package de.pinkhorizon.lobby.commands;

import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BuildCommand implements CommandExecutor, Listener {

    private final PHLobby plugin;
    private final Set<UUID> builders = new HashSet<>();

    public BuildCommand(PHLobby plugin) {
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

        if (builders.contains(player.getUniqueId())) {
            builders.remove(player.getUniqueId());
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage(Component.text(
                "§8[Build] §cBuild-Modus deaktiviert.", NamedTextColor.RED));
        } else {
            builders.add(player.getUniqueId());
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(Component.text(
                "§8[Build] §aBuild-Modus aktiviert. /build zum Deaktivieren.", NamedTextColor.GREEN));
        }
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        builders.remove(event.getPlayer().getUniqueId());
    }

    public boolean isBuildMode(UUID uuid) { return builders.contains(uuid); }
}
