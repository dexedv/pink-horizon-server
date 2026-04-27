package de.pinkhorizon.companions;

import de.pinkhorizon.companions.managers.CompanionManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.pinkhorizon.companions.gui.GuiBase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PHCompanions extends JavaPlugin implements Listener {

    private static PHCompanions instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private CompanionManager companionManager;

    @Override
    public void onEnable() {
        instance         = this;
        saveDefaultConfig();
        companionManager = new CompanionManager(this);

        getCommand("companion").setExecutor(this::onCommand);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("PH-Companions v1.0.0 gestartet!");
    }

    private boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur für Spieler.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "list" -> companionManager.showList(player);

            case "summon", "beschwören", "beschwore" -> {
                if (args.length < 2) { player.sendMessage(MM.deserialize("<red>Usage: /companion summon <name>")); return true; }
                CompanionType type = resolveType(player, args[1]);
                if (type != null) companionManager.summonCompanion(player, type);
            }

            case "dismiss", "verabschieden" -> {
                if (args.length < 2) { player.sendMessage(MM.deserialize("<red>Usage: /companion dismiss <name>")); return true; }
                CompanionType type = resolveType(player, args[1]);
                if (type != null) companionManager.dismissCompanion(player, type);
            }

            case "feed", "füttern", "futtern" -> {
                if (args.length < 2) { player.sendMessage(MM.deserialize("<red>Usage: /companion feed <name>")); return true; }
                CompanionType type = resolveType(player, args[1]);
                if (type != null) companionManager.feedCompanion(player, type);
            }

            case "info" -> {
                if (args.length < 2) { player.sendMessage(MM.deserialize("<red>Usage: /companion info <name>")); return true; }
                CompanionType type = resolveType(player, args[1]);
                if (type != null) companionManager.showInfo(player, type);
            }

            case "give" -> {
                if (!player.hasPermission("companions.admin")) {
                    player.sendMessage(MM.deserialize("<red>Keine Berechtigung."));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(MM.deserialize("<red>Usage: /companion give <Spieler> <type>"));
                    return true;
                }
                Player target = getServer().getPlayer(args[1]);
                if (target == null) { player.sendMessage(MM.deserialize("<red>Spieler nicht gefunden.")); return true; }
                CompanionType type = resolveType(player, args[2]);
                if (type != null) {
                    companionManager.grantCompanion(target, type);
                    player.sendMessage(MM.deserialize("<green>" + type.displayName + " wurde " + target.getName() + " gegeben."));
                }
            }

            default -> sendHelp(player);
        }
        return true;
    }

    private CompanionType resolveType(Player player, String name) {
        // Suche nach displayName zuerst, dann nach enum-Name
        for (CompanionType t : CompanionType.values()) {
            if (t.name().equalsIgnoreCase(name) || t.displayName.equalsIgnoreCase(name)) return t;
        }
        // Fuzzy match (Vorname reicht: "Gustav" → GOLEM_GUSTAV)
        for (CompanionType t : CompanionType.values()) {
            if (t.displayName.toLowerCase().contains(name.toLowerCase())) return t;
        }
        player.sendMessage(MM.deserialize("<red>Unbekannter Begleiter: <white>" + name
            + "<red>. Nutze <white>/companion list <red>für eine Übersicht."));
        return null;
    }

    private void sendHelp(Player player) {
        player.sendMessage(MM.deserialize("<gold>══ Begleiter-System ══"));
        player.sendMessage(MM.deserialize("<gray>/companion list <white>– Alle Begleiter anzeigen"));
        player.sendMessage(MM.deserialize("<gray>/companion summon <name> <white>– Begleiter beschwören"));
        player.sendMessage(MM.deserialize("<gray>/companion dismiss <name> <white>– Begleiter verabschieden"));
        player.sendMessage(MM.deserialize("<gray>/companion feed <name> <white>– Begleiter füttern (Item in Hand)"));
        player.sendMessage(MM.deserialize("<gray>/companion info <name> <white>– Info anzeigen"));
    }

    // ── Events ───────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiBase gui)) return;
        event.setCancelled(true);
        gui.handleClick(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Begleiter beim Logout despawnen (werden beim nächsten Login wieder geladen)
        companionManager.despawnAllForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onCompanionDamage(EntityDamageByEntityEvent event) {
        // Begleiter sind unverwundbar (setInvulnerable reicht nicht immer)
        if (companionManager.isCompanionEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    @Override
    public void onDisable() {
        if (companionManager != null) companionManager.close();
        getLogger().info("PH-Companions gestoppt.");
    }

    public static PHCompanions getInstance()           { return instance; }
    public CompanionManager getCompanionManager()      { return companionManager; }
}
