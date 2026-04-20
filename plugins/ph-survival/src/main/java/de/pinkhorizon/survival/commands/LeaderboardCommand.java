package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SurvivalHologramManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private static final int TOP = 10;

    private final PHSurvival plugin;
    private final File lbFile;
    private final YamlConfiguration lbConfig;

    public LeaderboardCommand(PHSurvival plugin) {
        this.plugin = plugin;
        lbFile = new File(plugin.getDataFolder(), "leaderboards.yml");
        lbConfig = YamlConfiguration.loadConfiguration(lbFile);
        // Refresh holograms every 5 minutes (6000 ticks)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshAll, 6000L, 6000L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur Spieler!"); return true; }

        // /lb sethere <type> – Admin
        if (args.length == 2 && args[0].equalsIgnoreCase("sethere")) {
            if (!player.hasPermission("survival.admin")) { player.sendMessage(Component.text("§cKein Zugriff!")); return true; }
            String type = args[1].toLowerCase();
            if (!Set.of("coins", "kills", "playtime").contains(type)) {
                player.sendMessage(Component.text("§cTyp: §ecoins §8| §ekills §8| §eplaytime"));
                return true;
            }
            var loc = player.getLocation();
            lbConfig.set(type + ".world", loc.getWorld().getName());
            lbConfig.set(type + ".x", loc.getX());
            lbConfig.set(type + ".y", loc.getY());
            lbConfig.set(type + ".z", loc.getZ());
            saveLbConfig();
            player.sendMessage(Component.text("§aLeaderboard §f" + type + " §ahier gesetzt. Wird gleich aktualisiert."));
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> refreshType(type));
            return true;
        }

        // /lb remove <type> – Admin
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            if (!player.hasPermission("survival.admin")) { player.sendMessage(Component.text("§cKein Zugriff!")); return true; }
            String type = args[1].toLowerCase();
            lbConfig.set(type, null);
            saveLbConfig();
            plugin.getHologramManager().remove("lb_" + type);
            player.sendMessage(Component.text("§7Leaderboard §f" + type + " §7entfernt."));
            return true;
        }

        // /lb [type] – show in chat
        String type = args.length > 0 ? args[0].toLowerCase() : "coins";
        switch (type) {
            case "coins"    -> showCoins(player);
            case "kills"    -> showKills(player);
            case "playtime" -> showPlaytime(player);
            default -> {
                player.sendMessage(Component.text("§cTyp: §ecoins §8| §ekills §8| §eplaytime"));
                player.sendMessage(Component.text("§cAdmin: §e/lb sethere <type> §8| §e/lb remove <type>"));
            }
        }
        return true;
    }

    // ── Chat display ─────────────────────────────────────────────────────

    private void showCoins(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var top = plugin.getEconomyManager().getTopCoins(TOP);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(Component.text("§6§l── Top " + TOP + " Coins ──"));
                int rank = 1;
                for (var entry : top) {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = op.getName() != null ? op.getName() : "???";
                    player.sendMessage(Component.text(rankColor(rank) + rank + ". §f" + name + " §8- §e" + entry.getValue() + " Coins"));
                    rank++;
                }
            });
        });
    }

    private void showKills(Player player) {
        var sm = plugin.getStatsManager();
        List<Map.Entry<String, Integer>> top = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            top.add(Map.entry(p.getName(), sm.getMobKills(p.getUniqueId())));
        }
        // Also offline players from YAML would require iterating stats.yml – show online only for simplicity
        top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        player.sendMessage(Component.text("§6§l── Top Mob-Kills (Online) ──"));
        int rank = 1;
        for (var e : top.subList(0, Math.min(TOP, top.size()))) {
            player.sendMessage(Component.text(rankColor(rank) + rank + ". §f" + e.getKey() + " §8- §e" + e.getValue() + " Kills"));
            rank++;
        }
    }

    private void showPlaytime(Player player) {
        var sm = plugin.getStatsManager();
        List<Map.Entry<String, Long>> top = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            top.add(Map.entry(p.getName(), sm.getPlaytime(p.getUniqueId())));
        }
        top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        player.sendMessage(Component.text("§6§l── Top Spielzeit (Online) ──"));
        int rank = 1;
        for (var e : top.subList(0, Math.min(TOP, top.size()))) {
            long h = e.getValue() / 60;
            long m = e.getValue() % 60;
            player.sendMessage(Component.text(rankColor(rank) + rank + ". §f" + e.getKey() + " §8- §e" + h + "h " + m + "m"));
            rank++;
        }
    }

    // ── Hologram refresh ─────────────────────────────────────────────────

    private void refreshAll() {
        for (String type : List.of("coins", "kills", "playtime")) {
            if (lbConfig.contains(type)) refreshType(type);
        }
    }

    private void refreshType(String type) {
        switch (type) {
            case "coins"    -> refreshCoins();
            case "kills"    -> refreshKills();
            case "playtime" -> refreshPlaytime();
        }
    }

    private void refreshCoins() {
        if (!lbConfig.contains("coins")) return;
        var top = plugin.getEconomyManager().getTopCoins(TOP);
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<String> lines = new ArrayList<>();
            lines.add("<gold><bold>Top " + TOP + " Coins</bold></gold>");
            lines.add("<gray>─────────────────</gray>");
            int rank = 1;
            for (var entry : top) {
                @SuppressWarnings("deprecation")
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                String name = op.getName() != null ? op.getName() : "???";
                String color = rank == 1 ? "<gold>" : rank == 2 ? "<gray>" : rank == 3 ? "<#CD7F32>" : "<white>";
                lines.add(color + rank + ". " + name + " <yellow>" + entry.getValue() + " Coins");
                rank++;
            }
            var loc = getLocation("coins");
            if (loc == null) return;
            plugin.getHologramManager().create("lb_coins", loc, lines, 0.9f);
        });
    }

    private void refreshKills() {
        if (!lbConfig.contains("kills")) return;
        var top = plugin.getStatsManager().getTopMobKills(TOP);
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<String> lines = new ArrayList<>();
            lines.add("<gold><bold>Top " + TOP + " Mob-Kills</bold></gold>");
            lines.add("<gray>─────────────────</gray>");
            int rank = 1;
            for (var entry : top) {
                @SuppressWarnings("deprecation")
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                String name = op.getName() != null ? op.getName() : "???";
                String color = rank == 1 ? "<gold>" : rank == 2 ? "<gray>" : rank == 3 ? "<#CD7F32>" : "<white>";
                lines.add(color + rank + ". " + name + " <yellow>" + entry.getValue() + " Kills");
                rank++;
            }
            var loc = getLocation("kills");
            if (loc == null) return;
            plugin.getHologramManager().create("lb_kills", loc, lines, 0.9f);
        });
    }

    private void refreshPlaytime() {
        if (!lbConfig.contains("playtime")) return;
        var top = plugin.getStatsManager().getTopPlaytime(TOP);
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<String> lines = new ArrayList<>();
            lines.add("<gold><bold>Top " + TOP + " Spielzeit</bold></gold>");
            lines.add("<gray>─────────────────</gray>");
            int rank = 1;
            for (var entry : top) {
                @SuppressWarnings("deprecation")
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                String name = op.getName() != null ? op.getName() : "???";
                String color = rank == 1 ? "<gold>" : rank == 2 ? "<gray>" : rank == 3 ? "<#CD7F32>" : "<white>";
                long h = entry.getValue() / 60;
                long m = entry.getValue() % 60;
                lines.add(color + rank + ". " + name + " <yellow>" + h + "h " + m + "m");
                rank++;
            }
            var loc = getLocation("playtime");
            if (loc == null) return;
            plugin.getHologramManager().create("lb_playtime", loc, lines, 0.9f);
        });
    }

    private org.bukkit.Location getLocation(String type) {
        if (!lbConfig.contains(type + ".world")) return null;
        var world = Bukkit.getWorld(lbConfig.getString(type + ".world", "world"));
        if (world == null) return null;
        return new org.bukkit.Location(world,
            lbConfig.getDouble(type + ".x"),
            lbConfig.getDouble(type + ".y"),
            lbConfig.getDouble(type + ".z"));
    }

    private String rankColor(int rank) {
        return switch (rank) { case 1 -> "§6"; case 2 -> "§7"; case 3 -> "§c"; default -> "§f"; };
    }

    private void saveLbConfig() {
        try { lbConfig.save(lbFile); }
        catch (IOException ex) { plugin.getLogger().warning("leaderboards.yml nicht gespeichert: " + ex.getMessage()); }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("coins", "kills", "playtime", "sethere", "remove");
        if (args.length == 2 && (args[0].equalsIgnoreCase("sethere") || args[0].equalsIgnoreCase("remove")))
            return List.of("coins", "kills", "playtime");
        return List.of();
    }
}
