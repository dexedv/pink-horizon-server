package de.pinkhorizon.minigames.commands;

import de.pinkhorizon.minigames.PHMinigames;
import de.pinkhorizon.minigames.VoidGenerator;
import de.pinkhorizon.minigames.bedwars.BedWarsGame;
import de.pinkhorizon.minigames.bedwars.BedWarsTeamColor;
import de.pinkhorizon.minigames.managers.BedWarsStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BedWarsCommand implements CommandExecutor, TabCompleter {

    private final PHMinigames plugin;

    public BedWarsCommand(PHMinigames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "join" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                BedWarsGame existing = plugin.getArenaManager().getGameOf(player.getUniqueId());
                if (existing != null) { player.sendMessage("§cDu bist bereits in einem Spiel."); return true; }

                String arenaName = args.length > 1 ? args[1] : null;
                BedWarsGame game = arenaName != null
                        ? plugin.getArenaManager().findOrCreateGame(arenaName)
                        : plugin.getArenaManager().findOrCreateAnyGame();

                if (game == null) { player.sendMessage("§cKeine verfügbare Arena gefunden."); return true; }
                if (!game.addPlayer(player)) player.sendMessage("§cDie Arena ist voll oder das Spiel läuft bereits.");
            }

            case "leave" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                BedWarsGame game = plugin.getArenaManager().getGameOf(player.getUniqueId());
                if (game == null) { player.sendMessage("§cDu bist in keinem Spiel."); return true; }
                game.removePlayer(player, true);
                player.sendMessage("§aDu hast das Spiel verlassen.");
            }

            case "shop" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                plugin.getShopGui().open(player);
            }

            case "stats" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                int wins   = plugin.getStatsManager().getStat(player.getUniqueId(), "wins");
                int losses = plugin.getStatsManager().getStat(player.getUniqueId(), "losses");
                int kills  = plugin.getStatsManager().getStat(player.getUniqueId(), "kills");
                int beds   = plugin.getStatsManager().getStat(player.getUniqueId(), "beds_broken");
                player.sendMessage("§d§lDeine BedWars-Stats:");
                player.sendMessage("§7Siege: §a" + wins + " §8| §7Niederlagen: §c" + losses);
                player.sendMessage("§7Kills: §e" + kills + " §8| §7Betten: §6" + beds);
            }

            case "create" -> {
                if (!sender.hasPermission("minigames.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
                if (args.length < 2) { sender.sendMessage("§cNutzung: /bw create <name> [maxTeams] [teamSize]"); return true; }
                String name = args[1];
                int maxTeams = args.length > 2 ? parseInt(args[2], 4) : 4;
                int teamSize = args.length > 3 ? parseInt(args[3], 2) : 2;
                String world = (sender instanceof Player p) ? p.getWorld().getName() : "world";
                if (plugin.getArenaManager().createArena(name, world, maxTeams, teamSize)) {
                    sender.sendMessage("§aArena §f" + name + " §aerstellt (" + maxTeams + " Teams, " + teamSize + " Spieler/Team).");
                } else {
                    sender.sendMessage("§cFehler oder Arena existiert bereits.");
                }
            }

            case "setspawn" -> {
                if (!sender.hasPermission("minigames.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                if (args.length < 3) { sender.sendMessage("§cNutzung: /bw setspawn <arena> <RED|BLUE|GREEN|YELLOW>"); return true; }
                BedWarsTeamColor color = BedWarsTeamColor.fromString(args[2]);
                if (color == null) { player.sendMessage("§cUnbekannte Teamfarbe. Verfügbar: RED, BLUE, GREEN, YELLOW"); return true; }
                if (plugin.getArenaManager().setSpawn(args[1], color, player.getLocation())) {
                    player.sendMessage("§aSpawn für §f" + color.displayName + " §ain §f" + args[1] + " §agesetzt.");
                } else {
                    player.sendMessage("§cArena §f" + args[1] + " §cnicht gefunden.");
                }
            }

            case "setbed" -> {
                if (!sender.hasPermission("minigames.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                if (args.length < 3) { sender.sendMessage("§cNutzung: /bw setbed <arena> <RED|BLUE|GREEN|YELLOW>"); return true; }
                BedWarsTeamColor color = BedWarsTeamColor.fromString(args[2]);
                if (color == null) { player.sendMessage("§cUnbekannte Teamfarbe."); return true; }
                Block target = player.getTargetBlockExact(10);
                if (target == null) { player.sendMessage("§cSchaue auf einen Block."); return true; }
                if (plugin.getArenaManager().setBed(args[1], color, target.getX(), target.getY(), target.getZ())) {
                    player.sendMessage("§aBett für §f" + color.displayName + " §aauf " + target.getX() + "/" + target.getY() + "/" + target.getZ() + " §aregistriert.");
                } else {
                    player.sendMessage("§cArena nicht gefunden.");
                }
            }

            case "addspawner" -> {
                if (!sender.hasPermission("minigames.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                if (args.length < 3) { sender.sendMessage("§cNutzung: /bw addspawner <arena> <IRON|GOLD|DIAMOND|EMERALD>"); return true; }
                String type = args[2].toUpperCase();
                if (!List.of("IRON","GOLD","DIAMOND","EMERALD").contains(type)) {
                    player.sendMessage("§cTyp muss IRON, GOLD, DIAMOND oder EMERALD sein.");
                    return true;
                }
                if (plugin.getArenaManager().addSpawner(args[1], type,
                        player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())) {
                    player.sendMessage("§a" + type + "-Spawner in §f" + args[1] + " §ahinzugefügt.");
                } else {
                    player.sendMessage("§cArena nicht gefunden.");
                }
            }

            case "hologram" -> {
                if (!sender.hasPermission("minigames.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                if (args.length < 3) { sender.sendMessage("§cNutzung: /bw hologram <create|delete> <name>"); return true; }
                String name = args[2];
                if (args[1].equalsIgnoreCase("create")) {
                    String[] lines = plugin.getHologramManager().buildLeaderboardLines();
                    plugin.getHologramManager().create("bw_top_" + name, player.getLocation(), lines);
                    player.sendMessage("§aLeaderboard-Hologramm §fbw_top_" + name + " §aerstellt.");
                } else if (args[1].equalsIgnoreCase("delete")) {
                    plugin.getHologramManager().remove("bw_top_" + name);
                    player.sendMessage("§aHologramm §fbw_top_" + name + " §aentfernt.");
                }
            }

            case "createworld" -> {
                if (!sender.hasPermission("minigames.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
                if (args.length < 2) { sender.sendMessage("§cNutzung: /bw createworld <name>"); return true; }
                String worldName = args[1];
                if (Bukkit.getWorld(worldName) != null) { sender.sendMessage("§cWelt §f" + worldName + " §cexistiert bereits."); return true; }
                sender.sendMessage("§7Erstelle Void-Welt §f" + worldName + "§7...");
                World world = new WorldCreator(worldName)
                        .generator(new VoidGenerator())
                        .createWorld();
                if (world != null) {
                    world.setSpawnLocation(0, 64, 0);
                    sender.sendMessage("§aVoid-Welt §f" + worldName + " §aerstellt! Teleportiere mit: §f/bw tpworld " + worldName);
                } else {
                    sender.sendMessage("§cFehler beim Erstellen der Welt.");
                }
            }

            case "tpworld" -> {
                if (!sender.hasPermission("minigames.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                if (args.length < 2) { sender.sendMessage("§cNutzung: /bw tpworld <name>"); return true; }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { player.sendMessage("§cWelt §f" + args[1] + " §cnicht gefunden. Erstelle sie mit /bw createworld."); return true; }
                player.teleport(world.getSpawnLocation());
                player.sendMessage("§aTeleportiert nach §f" + args[1]);
            }

            case "sethubspawn" -> {
                if (!sender.hasPermission("minigames.admin")) { sender.sendMessage("§cKeine Berechtigung."); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("§cNur für Spieler."); return true; }
                plugin.getHubManager().saveHubSpawn(player.getLocation());
                player.sendMessage("§aHub-Spawn auf deine aktuelle Position gesetzt.");
            }

            case "arenas" -> {
                sender.sendMessage("§d§lVerfügbare Arenen:");
                if (plugin.getArenaManager().getArenas().isEmpty()) {
                    sender.sendMessage("§7Keine Arenen konfiguriert.");
                    return true;
                }
                for (var cfg : plugin.getArenaManager().getArenas()) {
                    String status = cfg.isFullyConfigured() ? "§a✔" : "§c✗";
                    sender.sendMessage(status + " §f" + cfg.name + " §7(" + cfg.maxTeams + "T x " + cfg.teamSize + ") §7[" + cfg.world + "]");
                }
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return filter(List.of("join","leave","shop","stats","create","setspawn","setbed","addspawner","hologram","arenas","createworld","tpworld","sethubspawn"), args[0]);
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "join","setspawn","setbed","addspawner" -> {
                    List<String> names = new ArrayList<>();
                    plugin.getArenaManager().getArenas().forEach(c -> names.add(c.name));
                    yield filter(names, args[1]);
                }
                case "hologram" -> filter(List.of("create","delete"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "setspawn","setbed" -> filter(Arrays.stream(BedWarsTeamColor.values()).map(Enum::name).toList(), args[2]);
                case "addspawner"        -> filter(List.of("IRON","GOLD","DIAMOND","EMERALD"), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§d§lBedWars-Befehle:");
        sender.sendMessage("§7/bw join [arena] §8– §fSpiel beitreten");
        sender.sendMessage("§7/bw leave §8– §fSpiel verlassen");
        sender.sendMessage("§7/bw shop §8– §fShop öffnen");
        sender.sendMessage("§7/bw stats §8– §fDeine Stats anzeigen");
        sender.sendMessage("§7/bw arenas §8– §fArenen auflisten");
        if (sender.hasPermission("minigames.admin")) {
            sender.sendMessage("§c§lAdmin:");
            sender.sendMessage("§7/bw create <name> [teams] [größe]");
            sender.sendMessage("§7/bw setspawn <arena> <team>");
            sender.sendMessage("§7/bw setbed <arena> <team>");
            sender.sendMessage("§7/bw addspawner <arena> <typ>");
            sender.sendMessage("§7/bw hologram <create|delete> <name>");
            sender.sendMessage("§7/bw sethubspawn §8– §fHub-Spawn setzen");
        }
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).toList();
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
