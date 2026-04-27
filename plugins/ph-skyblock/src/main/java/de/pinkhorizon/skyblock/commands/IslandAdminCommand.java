package de.pinkhorizon.skyblock.commands;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class IslandAdminCommand implements CommandExecutor {

    private final PHSkyBlock plugin;

    public IslandAdminCommand(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "tp" -> cmdTp(sender, args);
            case "delete" -> cmdDelete(sender, args);
            case "info" -> cmdInfo(sender, args);
            case "setspawn" -> cmdSetSpawn(sender);
            case "reload" -> cmdReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void cmdTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) { sender.sendMessage("Nur für Spieler."); return; }
        if (args.length < 2) { sendHelp(sender); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(plugin.msg("player-not-found", "name", args[1])); return; }

        Island island = plugin.getIslandManager().getIslandByOwner(target.getUniqueId());
        if (island == null) { sender.sendMessage(plugin.msg("island-no-island")); return; }

        Location loc = new Location(Bukkit.getWorld(island.getWorld()),
            island.getHomeX(), island.getHomeY(), island.getHomeZ());
        admin.teleport(loc);
        admin.sendMessage(plugin.msg("island-home-tp"));
    }

    private void cmdDelete(CommandSender sender, String[] args) {
        if (args.length < 2) { sendHelp(sender); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(plugin.msg("player-not-found", "name", args[1])); return; }

        Island island = plugin.getIslandManager().getIslandByOwner(target.getUniqueId());
        if (island == null) { sender.sendMessage(plugin.msg("island-no-island")); return; }

        plugin.getIslandManager().deleteIsland(island);
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<green>Insel von <light_purple>" + target.getName() + "</light_purple> wurde gelöscht."));
    }

    private void cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sendHelp(sender); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(plugin.msg("player-not-found", "name", args[1])); return; }

        Island island = plugin.getIslandManager().getIslandOfPlayer(target.getUniqueId());
        if (island == null) { sender.sendMessage(plugin.msg("island-no-island")); return; }

        sender.sendMessage(MiniMessage.miniMessage().deserialize("<light_purple>━━━ Island Info ━━━"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>ID: <white>" + island.getId()));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>UUID: <white>" + island.getIslandUuid()));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Owner: <white>" + island.getOwnerName()));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Welt: <white>" + island.getWorld()));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Zentrum: <white>" + island.getCenterX() + ", " + island.getCenterY() + ", " + island.getCenterZ()));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Level: <white>" + island.getLevel() + " | Score: " + island.getScore()));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Mitglieder: <white>" + (island.getMembers().size() + 1) + "/" + island.getMaxMembers()));
    }

    private void cmdSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player admin)) { sender.sendMessage("Nur für Spieler."); return; }

        Location loc = admin.getLocation();
        plugin.getConfig().set("spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.x", loc.getX());
        plugin.getConfig().set("spawn.y", loc.getY());
        plugin.getConfig().set("spawn.z", loc.getZ());
        plugin.getConfig().set("spawn.yaw", (double) loc.getYaw());
        plugin.getConfig().set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
        admin.sendMessage(MiniMessage.miniMessage().deserialize("<green>Spawn gesetzt!"));
    }

    private void cmdReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Config neu geladen."));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<light_purple>━━━ isadmin Hilfe ━━━\n" +
            "<gray>/isadmin tp <Spieler>     – TP zur Insel\n" +
            "/isadmin delete <Spieler> – Insel löschen\n" +
            "/isadmin info <Spieler>   – Insel-Details\n" +
            "/isadmin setspawn         – Spawn setzen\n" +
            "/isadmin reload           – Config neu laden"));
    }
}
