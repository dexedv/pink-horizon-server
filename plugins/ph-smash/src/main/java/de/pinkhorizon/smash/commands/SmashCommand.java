package de.pinkhorizon.smash.commands;

import de.pinkhorizon.smash.PHSmash;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class SmashCommand implements CommandExecutor, TabCompleter {

    private final PHSmash plugin;

    public SmashCommand(PHSmash plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c§lSmash the Boss §8– §7/stb <join|leave|upgrades|stats|setarena|forceboss>");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "join" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                Location spawn = getArenaSpawn();
                p.teleport(spawn);
                p.sendMessage("§a✔ §7Du bist der Arena beigetreten!");
            }

            case "leave" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                p.teleport(getArenaSpawn()); // Könnte später zu Hub teleportieren
                p.sendMessage("§7Du hast die Arena verlassen.");
            }

            case "upgrades", "upgrade" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                plugin.getUpgradeGui().open(p);
            }

            case "stats" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                int  kills  = plugin.getPlayerDataManager().getKills(p.getUniqueId());
                long damage = plugin.getPlayerDataManager().getTotalDamage(p.getUniqueId());
                p.sendMessage("§c§l⚔ Deine Stats:");
                p.sendMessage("§7  Boss-Kills:     §a" + kills);
                p.sendMessage("§7  Gesamt-Schaden: §e" + damage);
                p.sendMessage("§7  Globaler Boss:  §c" + plugin.getPlayerDataManager().getGlobalBossLevel());
            }

            case "setarena" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("§cNur für Spieler!"); return true; }
                if (args.length < 2) { p.sendMessage("§c/stb setarena <spawn|boss>"); return true; }

                Location loc = p.getLocation();
                String key = args[1].equalsIgnoreCase("boss") ? "arena.boss" : "arena.spawn";
                plugin.getConfig().set(key + ".world", loc.getWorld().getName());
                plugin.getConfig().set(key + ".x",     loc.getX());
                plugin.getConfig().set(key + ".y",     loc.getY());
                plugin.getConfig().set(key + ".z",     loc.getZ());
                plugin.getConfig().set(key + ".yaw",   (double) loc.getYaw());
                plugin.getConfig().set(key + ".pitch", (double) loc.getPitch());
                plugin.saveConfig();
                p.sendMessage("§a✔ §f" + key + " §7gesetzt auf §f" + String.format("%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ()));
            }

            case "forceboss" -> {
                if (!sender.hasPermission("smash.admin")) { sender.sendMessage("§cKein Zugriff!"); return true; }
                int level = 1;
                if (args.length >= 2) {
                    try { level = Math.min(999, Math.max(1, Integer.parseInt(args[1]))); }
                    catch (NumberFormatException ignored) {}
                }
                plugin.getBossManager().removeBoss();
                plugin.getPlayerDataManager().setGlobalBossLevel(level);
                plugin.getBossManager().spawnBoss(level);
                sender.sendMessage("§a✔ §7Boss auf Level §c" + level + " §7gespawnt!");
            }

            default -> sender.sendMessage("§c§lSmash the Boss §8– §7/stb <join|leave|upgrades|stats|setarena|forceboss>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new java.util.ArrayList<>(List.of("join", "leave", "upgrades", "stats"));
            if (sender.hasPermission("smash.admin")) { base.add("setarena"); base.add("forceboss"); }
            return base.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setarena"))
            return List.of("spawn", "boss").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        return List.of();
    }

    private Location getArenaSpawn() {
        String world = plugin.getConfig().getString("arena.spawn.world", "world");
        double x     = plugin.getConfig().getDouble("arena.spawn.x", 0.5);
        double y     = plugin.getConfig().getDouble("arena.spawn.y", 64);
        double z     = plugin.getConfig().getDouble("arena.spawn.z", 0.5);
        float  yaw   = (float) plugin.getConfig().getDouble("arena.spawn.yaw", 0);
        float  pitch = (float) plugin.getConfig().getDouble("arena.spawn.pitch", 0);
        World  w     = Bukkit.getWorld(world);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w, x, y, z, yaw, pitch);
    }
}
