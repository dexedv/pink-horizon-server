package de.pinkhorizon.battlepass;

import de.pinkhorizon.battlepass.gui.GuiBase;
import de.pinkhorizon.battlepass.managers.BattlePassManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PHBattlePass extends JavaPlugin implements Listener {

    private static PHBattlePass instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private BattlePassManager battlePassManager;

    @Override
    public void onEnable() {
        instance          = this;
        saveDefaultConfig();
        battlePassManager = new BattlePassManager(this);

        getCommand("battlepass").setExecutor(this::onCommand);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("PH-BattlePass v1.0.0 gestartet!");
    }

    private boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Nur für Spieler."); return true; }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            battlePassManager.showGui(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "challenges" -> battlePassManager.showChallenges(player);

            case "claim" -> {
                if (args.length < 2) {
                    player.sendMessage(MM.deserialize("<red>Usage: /bp claim <level>"));
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[1]);
                    battlePassManager.claimReward(player, level);
                } catch (NumberFormatException e) {
                    player.sendMessage(MM.deserialize("<red>Ungültige Level-Nummer!"));
                }
            }

            case "premium" -> {
                if (!player.hasPermission("battlepass.admin")) {
                    player.sendMessage(MM.deserialize("<red>Keine Berechtigung."));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(MM.deserialize("<red>Usage: /bp premium <Spieler> <true|false>"));
                    return true;
                }
                Player target = getServer().getPlayer(args[1]);
                if (target == null) { player.sendMessage(MM.deserialize("<red>Spieler nicht gefunden.")); return true; }
                boolean premium = Boolean.parseBoolean(args[2]);
                battlePassManager.setPremium(target, premium);
                player.sendMessage(MM.deserialize("<green>" + target.getName() + " Premium: " + premium));
                target.sendMessage(premium
                    ? MM.deserialize("<gold>Du hast jetzt den <bold>Premium Battle Pass<gold>!")
                    : MM.deserialize("<gray>Dein Premium Battle Pass wurde entfernt."));
            }

            default -> battlePassManager.showGui(player);
        }
        return true;
    }

    // ── GUI-Event ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiBase gui)) return;
        event.setCancelled(true);
        gui.handleClick(event);
    }

    // ── Challenge-Tracking-Events ─────────────────────────────────────────────

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        battlePassManager.trackProgress(event.getPlayer(), "BLOCK_BREAK", 1);
    }

    @EventHandler
    public void onHarvest(PlayerHarvestBlockEvent event) {
        battlePassManager.trackProgress(event.getPlayer(), "HARVEST", 1);
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        if (!event.getEntity().getType().isAlive()) return;
        battlePassManager.trackProgress(event.getEntity().getKiller(), "MOB_KILL", 1);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH
            || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            battlePassManager.trackProgress(event.getPlayer(), "FISH", 1);
        }
    }

    @Override
    public void onDisable() {
        if (battlePassManager != null) battlePassManager.close();
        getLogger().info("PH-BattlePass gestoppt.");
    }

    public static PHBattlePass getInstance()            { return instance; }
    public BattlePassManager getBattlePassManager()     { return battlePassManager; }
}
