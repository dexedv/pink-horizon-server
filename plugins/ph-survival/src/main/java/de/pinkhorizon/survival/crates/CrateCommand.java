package de.pinkhorizon.survival.crates;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CrateCommand implements CommandExecutor, TabCompleter {

    private final PHSurvival   plugin;
    private final CrateManager manager;

    private static final List<String> TYPES = List.of("eco", "claims", "spawner");

    public CrateCommand(PHSurvival plugin, CrateManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set"    -> cmdSet(sender, args);
            case "remove" -> cmdRemove(sender);
            case "give"   -> cmdGive(sender, args);
            default       -> sendHelp(sender);
        }
        return true;
    }

    // ── /crate set <type> ─────────────────────────────────────────────────

    private void cmdSet(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) { noPerms(sender); return; }
        if (!(sender instanceof Player player)) { player(sender); return; }
        if (args.length < 2) { sender.sendMessage("§cVerwendung: /crate set <" + CrateManager.VALID_TYPES_STR + ">"); return; }

        String type = args[1].toLowerCase();
        if (!TYPES.contains(type)) {
            sender.sendMessage("§cUnbekannter Typ. Erlaubt: " + CrateManager.VALID_TYPES_STR);
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null || (target.getType() != Material.CHEST && target.getType() != Material.TRAPPED_CHEST)) {
            sender.sendMessage("§cBlicke auf eine Truhe (max. 5 Blöcke Entfernung).");
            return;
        }

        manager.addCrateLocation(type, target.getLocation());
        String name = CrateManager.CRATE_NAMES.getOrDefault(type, type);
        sender.sendMessage("§a✔ Truhe als §f" + name + "§a registriert.");
    }

    // ── /crate remove ─────────────────────────────────────────────────────

    private void cmdRemove(CommandSender sender) {
        if (!isAdmin(sender)) { noPerms(sender); return; }
        if (!(sender instanceof Player player)) { player(sender); return; }

        Block target = player.getTargetBlockExact(5);
        if (target == null) { sender.sendMessage("§cBlicke auf eine Truhe."); return; }

        if (manager.removeCrateLocation(target.getLocation())) {
            sender.sendMessage("§aTruhe erfolgreich entfernt.");
        } else {
            sender.sendMessage("§cDiese Truhe ist keine registrierte Crate.");
        }
    }

    // ── /crate give <player> <type> [amount] ──────────────────────────────

    private void cmdGive(CommandSender sender, String[] args) {
        // Console or admin may run this
        if (sender instanceof Player p && !isAdmin(p)) { noPerms(sender); return; }
        if (args.length < 3) { sender.sendMessage("§cVerwendung: /crate give <Spieler> <" + CrateManager.VALID_TYPES_STR + "> [Anzahl]"); return; }

        String targetName = args[1];
        String type       = args[2].toLowerCase();
        int    amount     = args.length >= 4 ? parseIntOrOne(args[3]) : 1;

        if (!TYPES.contains(type)) {
            sender.sendMessage("§cUnbekannter Typ. Erlaubt: " + CrateManager.VALID_TYPES_STR);
            return;
        }

        // Resolve target (online preferred, offline fallback)
        Player online = Bukkit.getPlayer(targetName);
        if (online == null) {
            sender.sendMessage("§cSpieler §f" + targetName + "§c ist nicht online.");
            return;
        }

        ItemStack key = manager.createKey(type);
        key.setAmount(Math.max(1, Math.min(amount, 64)));

        var leftover = online.getInventory().addItem(key);
        leftover.values().forEach(item ->
            online.getWorld().dropItemNaturally(online.getLocation(), item));

        String keyName = switch (type) {
            case "eco"     -> "§6Eco-Schlüssel";
            case "claims"  -> "§aClaims-Schlüssel";
            case "spawner" -> "§dSpawner-Schlüssel";
            default        -> "Schlüssel";
        };
        sender.sendMessage("§a" + amount + "x " + keyName + "§a an §f" + online.getName() + "§a gegeben.");
        online.sendMessage("§d§l[Truhe] §7Du hast §d" + amount + "x " + keyName + " §7erhalten!");
    }

    // ── Tab completion ─────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("set", "remove", "give");
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give")) {
                return args[0].equalsIgnoreCase("give")
                    ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                    : TYPES;
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return TYPES;
        return List.of();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean isAdmin(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("survival.admin");
    }

    private void noPerms(CommandSender s)  { s.sendMessage("§cKeine Berechtigung!"); }
    private void player(CommandSender s)   { s.sendMessage("§cNur für Spieler."); }
    private void sendHelp(CommandSender s) {
        s.sendMessage("§d/crate set <eco|claims|spawner> §7- Truhe registrieren");
        s.sendMessage("§d/crate remove §7- Truhe deregistrieren");
        s.sendMessage("§d/crate give <Spieler> <Typ> [Anzahl] §7- Schlüssel geben");
    }

    private int parseIntOrOne(String s) {
        try { return Math.max(1, Integer.parseInt(s)); } catch (NumberFormatException e) { return 1; }
    }
}
