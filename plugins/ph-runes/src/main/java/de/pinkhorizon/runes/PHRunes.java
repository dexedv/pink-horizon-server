package de.pinkhorizon.runes;

import de.pinkhorizon.runes.gui.GuiBase;
import de.pinkhorizon.runes.managers.RuneManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class PHRunes extends JavaPlugin implements Listener {

    private static PHRunes instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private RuneManager runeManager;

    @Override
    public void onEnable() {
        instance    = this;
        saveDefaultConfig();
        runeManager = new RuneManager(this);

        getCommand("rune").setExecutor(this::onCommand);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("PH-Runes v1.0.0 gestartet!");
    }

    private boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur für Spieler."); return true; }

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {

            case "list" -> runeManager.showRuneInventory(player);

            case "engrave", "gravieren" -> {
                if (args.length < 2) { player.sendMessage(MM.deserialize("<red>Usage: /rune engrave <rune-name>")); return true; }
                RuneType rune = resolveRune(player, args[1]);
                if (rune != null) runeManager.engraveRune(player, rune);
            }

            case "inspect", "inspizieren" -> runeManager.inspectItem(player);

            case "info" -> {
                if (args.length < 2) { player.sendMessage(MM.deserialize("<red>Usage: /rune info <rune-name>")); return true; }
                RuneType rune = resolveRune(player, args[1]);
                if (rune != null) {
                    player.sendMessage(MM.deserialize("<gold>══ " + rune.displayName + " ══"));
                    player.sendMessage(MM.deserialize("<gray>Effekt: <white>" + rune.description));
                    player.sendMessage(MM.deserialize("<gray>Slots: <white>" + rune.slotCost));
                    player.sendMessage(MM.deserialize("<gray>Erlaubt auf: <white>" + rune.allowedOn));
                }
            }

            case "give" -> {
                if (!player.hasPermission("runes.admin")) { player.sendMessage(MM.deserialize("<red>Keine Berechtigung.")); return true; }
                if (args.length < 3) { player.sendMessage(MM.deserialize("<red>Usage: /rune give <Spieler> <rune>")); return true; }
                Player target = getServer().getPlayer(args[1]);
                if (target == null) { player.sendMessage(MM.deserialize("<red>Spieler nicht gefunden.")); return true; }
                RuneType rune = resolveRune(player, args[2]);
                if (rune != null) {
                    runeManager.giveRune(target.getUniqueId(), rune, 1);
                    target.getInventory().addItem(runeManager.createRuneItem(rune));
                    player.sendMessage(MM.deserialize("<green>" + rune.displayName + " an " + target.getName() + " gegeben."));
                }
            }

            default -> sendHelp(player);
        }
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiBase gui)) return;
        event.setCancelled(true);
        gui.handleClick(event);
    }

    // Spieler hebt Runen-Item auf → automatisch ins Runen-Inventar
    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        RuneType rune = runeManager.getRuneTypeFromItem(item);
        if (rune == null) return;

        event.setCancelled(true);
        event.getItem().remove();

        runeManager.giveRune(event.getPlayer().getUniqueId(), rune, item.getAmount());
        event.getPlayer().sendMessage(MM.deserialize(
            "<gold>Rune aufgenommen: " + rune.colorPrefix + rune.displayName
            + " <gold>×" + item.getAmount()));
    }

    private RuneType resolveRune(Player player, String name) {
        for (RuneType r : RuneType.values()) {
            if (r.name().equalsIgnoreCase(name) || r.displayName.equalsIgnoreCase(name)) return r;
        }
        for (RuneType r : RuneType.values()) {
            if (r.displayName.toLowerCase().contains(name.toLowerCase())) return r;
        }
        player.sendMessage(MM.deserialize("<red>Unbekannte Rune: <white>" + name
            + "<red>. Nutze <white>/rune list <red>für eine Übersicht."));
        return null;
    }

    private void sendHelp(Player player) {
        player.sendMessage(MM.deserialize("<gold>══ Runen-System ══"));
        player.sendMessage(MM.deserialize("<gray>/rune list <white>– Alle deine Runen"));
        player.sendMessage(MM.deserialize("<gray>/rune engrave <name> <white>– Rune in Item-in-Hand gravieren"));
        player.sendMessage(MM.deserialize("<gray>/rune inspect <white>– Item-in-Hand auf Runen untersuchen"));
        player.sendMessage(MM.deserialize("<gray>/rune info <name> <white>– Info zu einer Rune"));
    }

    @Override
    public void onDisable() {
        if (runeManager != null) runeManager.close();
        getLogger().info("PH-Runes gestoppt.");
    }

    public static PHRunes getInstance()   { return instance; }
    public RuneManager getRuneManager()   { return runeManager; }
}
