package de.pinkhorizon.dungeons;

import de.pinkhorizon.dungeons.gui.GuiBase;
import de.pinkhorizon.dungeons.managers.DungeonManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PHDungeons extends JavaPlugin implements Listener {

    private static PHDungeons instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private DungeonManager dungeonManager;

    @Override
    public void onEnable() {
        instance        = this;
        saveDefaultConfig();
        dungeonManager  = new DungeonManager(this);

        getCommand("dungeon").setExecutor(this::onCommand);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("PH-Dungeons v1.0.0 gestartet!");
    }

    private boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur für Spieler."); return true; }

        if (args.length == 0) { dungeonManager.showList(player); return true; }

        switch (args[0].toLowerCase()) {

            case "list" -> dungeonManager.showList(player);

            case "enter", "betreten" -> {
                if (args.length < 2) {
                    player.sendMessage(MM.deserialize("<red>Usage: /dungeon enter <dungeon>"));
                    return true;
                }
                DungeonType type = resolveType(player, args[1]);
                if (type != null) dungeonManager.startDungeon(player, type);
            }

            case "invite", "einladen" -> {
                if (args.length < 2) {
                    player.sendMessage(MM.deserialize("<red>Usage: /dungeon invite <Spieler>"));
                    return true;
                }
                Player target = getServer().getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(MM.deserialize("<red>Spieler nicht gefunden."));
                    return true;
                }
                dungeonManager.invitePlayer(player, target);
            }

            case "accept", "annehmen" -> dungeonManager.acceptInvite(player);

            case "decline", "ablehnen" -> {
                player.sendMessage(MM.deserialize("<gray>Einladung abgelehnt."));
                // pendingInvites wird automatisch durch den Timer bereinigt
            }

            case "leave", "verlassen" -> dungeonManager.leaveDungeon(player);

            case "status" -> dungeonManager.showStatus(player);

            case "give" -> {
                if (!player.hasPermission("dungeons.admin")) {
                    player.sendMessage(MM.deserialize("<red>Keine Berechtigung."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(MM.deserialize("<red>Usage: /dungeon give <dungeon>"));
                    return true;
                }
                DungeonType type = resolveType(player, args[1]);
                if (type != null) {
                    player.getInventory().addItem(dungeonManager.createDungeonCard(type));
                    player.sendMessage(MM.deserialize("<green>Dungeon-Karte erhalten: <white>" + type.displayName));
                }
            }

            default -> dungeonManager.showList(player);
        }
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiBase gui)) return;
        event.setCancelled(true);
        gui.handleClick(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (dungeonManager.isInDungeon(event.getPlayer())) {
            dungeonManager.leaveDungeon(event.getPlayer());
        }
    }

    private DungeonType resolveType(Player player, String name) {
        for (DungeonType t : DungeonType.values()) {
            if (t.name().equalsIgnoreCase(name) || t.displayName.equalsIgnoreCase(name)) return t;
        }
        for (DungeonType t : DungeonType.values()) {
            if (t.displayName.toLowerCase().contains(name.toLowerCase())) return t;
        }
        player.sendMessage(MM.deserialize("<red>Unbekannter Dungeon: <white>" + name));
        player.sendMessage(MM.deserialize("<gray>Verfügbar: " + String.join(", ",
            java.util.Arrays.stream(DungeonType.values()).map(t -> t.name().toLowerCase()).toList())));
        return null;
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) dungeonManager.close();
        getLogger().info("PH-Dungeons gestoppt.");
    }

    public static PHDungeons getInstance()      { return instance; }
    public DungeonManager getDungeonManager()   { return dungeonManager; }
}
