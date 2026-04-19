package de.pinkhorizon.skyblock.commands;

import de.pinkhorizon.skyblock.PHSkyBlock;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class IslandCommand implements CommandExecutor {

    private final PHSkyBlock plugin;
    // Cooldown fuer Reset: UUID -> Zeitstempel
    private final HashMap<java.util.UUID, Long> resetCooldown = new HashMap<>();
    private static final long RESET_COOLDOWN_MS = 60_000L; // 60 Sekunden

    public IslandCommand(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "help";

        switch (sub) {
            case "create" -> {
                if (plugin.getIslandManager().hasIsland(player.getUniqueId())) {
                    player.sendMessage("\u00a7cDu hast bereits eine Insel! Nutze /island home");
                    return true;
                }
                player.sendMessage("\u00a7dDeine Insel wird erstellt...");
                plugin.getIslandManager().createIsland(player.getUniqueId());
                player.teleport(plugin.getIslandManager().getHome(player.getUniqueId()));
                giveStarterKit(player);
                player.sendMessage("\u00a7aWillkommen auf deiner Insel! Du hast ein Starter-Kit erhalten.");
            }
            case "home" -> {
                if (!plugin.getIslandManager().hasIsland(player.getUniqueId())) {
                    player.sendMessage("\u00a7cDu hast noch keine Insel! Erstelle eine mit /island create");
                    return true;
                }
                player.teleport(plugin.getIslandManager().getHome(player.getUniqueId()));
                player.sendMessage("\u00a7aZurueck auf deiner Insel!");
            }
            case "reset" -> {
                if (!plugin.getIslandManager().hasIsland(player.getUniqueId())) {
                    player.sendMessage("\u00a7cDu hast keine Insel!");
                    return true;
                }
                // Cooldown pruefen
                long last = resetCooldown.getOrDefault(player.getUniqueId(), 0L);
                long remaining = RESET_COOLDOWN_MS - (System.currentTimeMillis() - last);
                if (remaining > 0) {
                    player.sendMessage("\u00a7cBitte warte noch " + (remaining / 1000) + " Sekunden.");
                    return true;
                }
                // Bestaetigung erforderlich
                if (args.length < 2 || !args[1].equalsIgnoreCase("bestaetigen")) {
                    player.sendMessage("\u00a7cACHTUNG! \u00a77Deine Insel wird zurueckgesetzt!");
                    player.sendMessage("\u00a77Tippe \u00a7c/island reset bestaetigen \u00a77um fortzufahren.");
                    return true;
                }
                resetCooldown.put(player.getUniqueId(), System.currentTimeMillis());
                plugin.getIslandManager().resetIsland(player.getUniqueId());
                player.teleport(plugin.getIslandManager().getHome(player.getUniqueId()));
                giveStarterKit(player);
                player.sendMessage("\u00a7aDeine Insel wurde zurueckgesetzt!");
            }
            case "info" -> {
                if (!plugin.getIslandManager().hasIsland(player.getUniqueId())) {
                    player.sendMessage("\u00a7cDu hast noch keine Insel!");
                    return true;
                }
                var home = plugin.getIslandManager().getHome(player.getUniqueId());
                int level = plugin.getIslandManager().getLevel(player.getUniqueId());
                player.sendMessage("\u00a7d=== Deine Insel ===");
                player.sendMessage("\u00a77Position: \u00a7f" + home.getBlockX() + ", " + home.getBlockY() + ", " + home.getBlockZ());
                player.sendMessage("\u00a77Level:     \u00a7f" + level);
            }
            default -> player.sendMessage("\u00a7d/island \u00a7f<create | home | reset | info>");
        }
        return true;
    }

    private void giveStarterKit(Player player) {
        player.getInventory().clear();
        player.getInventory().addItem(
                new ItemStack(Material.OAK_LOG, 32),
                new ItemStack(Material.CRAFTING_TABLE),
                new ItemStack(Material.FURNACE),
                new ItemStack(Material.COOKED_BEEF, 16),
                new ItemStack(Material.BONE_MEAL, 8),
                new ItemStack(Material.ICE, 2),
                new ItemStack(Material.LAVA_BUCKET)
        );
    }
}
