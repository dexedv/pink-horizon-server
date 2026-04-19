package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class KitCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival plugin;
    private final File cooldownFile;
    private final YamlConfiguration cooldowns;

    private static final long COOLDOWN_MS = 24 * 60 * 60 * 1000L; // 24 Stunden

    public KitCommand(PHSurvival plugin) {
        this.plugin = plugin;
        cooldownFile = new File(plugin.getDataFolder(), "kit_cooldowns.yml");
        cooldowns = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cVerwendung: /kit <starter>");
            player.sendMessage("§7Verfügbare Kits: §fstarter");
            return true;
        }

        if (!args[0].equalsIgnoreCase("starter")) {
            player.sendMessage("§cUnbekanntes Kit! Verfügbar: §fstarter");
            return true;
        }

        UUID uuid = player.getUniqueId();
        long lastUsed = cooldowns.getLong(uuid + ".starter", 0L);
        long now = System.currentTimeMillis();
        long remaining = COOLDOWN_MS - (now - lastUsed);

        if (remaining > 0 && !player.isOp()) {
            long hours = remaining / 3600000;
            long minutes = (remaining % 3600000) / 60000;
            player.sendMessage("§cDu kannst das Kit erst in §f" + hours + "h " + minutes + "m §cwieder verwenden!");
            return true;
        }

        giveStarterKit(player);
        cooldowns.set(uuid + ".starter", now);
        try { cooldowns.save(cooldownFile); } catch (IOException ignored) {}
        player.sendMessage("§aStarter-Kit erhalten!");
        return true;
    }

    private void giveStarterKit(Player player) {
        ItemStack[] items = {
            new ItemStack(Material.STONE_SWORD),
            new ItemStack(Material.STONE_PICKAXE),
            new ItemStack(Material.STONE_AXE),
            new ItemStack(Material.STONE_SHOVEL),
            new ItemStack(Material.BREAD, 32),
            new ItemStack(Material.TORCH, 16),
            new ItemStack(Material.OAK_LOG, 32),
            new ItemStack(Material.IRON_HELMET),
            new ItemStack(Material.IRON_CHESTPLATE),
            new ItemStack(Material.IRON_LEGGINGS),
            new ItemStack(Material.IRON_BOOTS),
        };
        for (ItemStack item : items) {
            player.getInventory().addItem(item).forEach((slot, leftover) ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("starter");
        return List.of();
    }
}
