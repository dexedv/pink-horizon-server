package de.pinkhorizon.minigames.listeners;

import de.pinkhorizon.minigames.PHMinigames;
import de.pinkhorizon.minigames.bedwars.BedWarsGame;
import de.pinkhorizon.minigames.bedwars.BedWarsTeamColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Villager;

import java.util.Set;

public class BedWarsListener implements Listener {

    private static final Set<Material> BED_MATERIALS = Set.of(
            Material.RED_BED, Material.BLUE_BED, Material.GREEN_BED, Material.YELLOW_BED,
            Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED, Material.LIGHT_BLUE_BED,
            Material.LIME_BED, Material.PINK_BED, Material.GRAY_BED, Material.LIGHT_GRAY_BED,
            Material.CYAN_BED, Material.PURPLE_BED, Material.BROWN_BED, Material.BLACK_BED
    );

    private final PHMinigames plugin;

    public BedWarsListener(PHMinigames plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        BedWarsGame game = plugin.getArenaManager().getGameOf(victim.getUniqueId());
        if (game == null || game.getState() != BedWarsGame.GameState.RUNNING) return;

        event.setCancelled(true);
        event.setDeathMessage(null);

        Player killer = victim.getKiller();
        if (killer != null && game.isInGame(killer.getUniqueId())) {
            plugin.getStatsManager().incrementStat(killer.getUniqueId(), "kills");
        }

        game.handleDeath(victim, killer);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BedWarsGame game = plugin.getArenaManager().getGameOf(player.getUniqueId());
        if (game == null || game.getState() != BedWarsGame.GameState.RUNNING) return;

        Block block = event.getBlock();

        // Bett-Check
        if (BED_MATERIALS.contains(block.getType())) {
            BedWarsTeamColor bedTeam = game.getBedTeamAt(
                    block.getX(), block.getY(), block.getZ(),
                    block.getWorld().getName());
            if (bedTeam != null) {
                // Eigenes Bett nicht zerstörbar
                BedWarsTeamColor playerTeam = game.getTeamOf(player.getUniqueId());
                if (bedTeam == playerTeam) {
                    event.setCancelled(true);
                    player.sendMessage("§cDu kannst dein eigenes Bett nicht zerstören!");
                    return;
                }
                // Gegnerisches Bett zerstören
                event.setDropItems(false);
                game.destroyBed(bedTeam, player);
            }
        }
        // Alle anderen Blöcke können in der Spielwelt gebrochen werden (Spieler bauen)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        BedWarsGame game = plugin.getArenaManager().getGameOf(player.getUniqueId());
        if (game == null) return;

        if (game.getState() == BedWarsGame.GameState.WAITING
                || game.getState() == BedWarsGame.GameState.STARTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        BedWarsGame game = plugin.getArenaManager().getGameOf(player.getUniqueId());
        if (game == null) return;

        // Kein Schaden in Wartephase
        if (game.getState() == BedWarsGame.GameState.WAITING
                || game.getState() == BedWarsGame.GameState.STARTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        BedWarsGame victimGame   = plugin.getArenaManager().getGameOf(victim.getUniqueId());
        BedWarsGame attackerGame = plugin.getArenaManager().getGameOf(attacker.getUniqueId());

        if (victimGame == null || victimGame != attackerGame) return;
        if (victimGame.getState() != BedWarsGame.GameState.RUNNING) return;

        // Kein Team-Schaden
        BedWarsTeamColor victimTeam   = victimGame.getTeamOf(victim.getUniqueId());
        BedWarsTeamColor attackerTeam = victimGame.getTeamOf(attacker.getUniqueId());
        if (victimTeam == attackerTeam) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        BedWarsGame game = plugin.getArenaManager().getGameOf(player.getUniqueId());
        if (game == null) return;
        if (game.getState() == BedWarsGame.GameState.WAITING
                || game.getState() == BedWarsGame.GameState.STARTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        BedWarsGame game = plugin.getArenaManager().getGameOf(event.getPlayer().getUniqueId());
        if (game != null && game.getState() != BedWarsGame.GameState.RUNNING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        BedWarsGame game = plugin.getArenaManager().getGameOf(player.getUniqueId());
        if (game == null) return;

        // Shop-Inventory-Klick weiterleiten
        if (event.getView().getTitle().startsWith("§d§lBedWars-Shop")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null
                    || event.getCurrentItem().getType() == Material.AIR) return;
            plugin.getShopGui().handleClick(player, event.getCurrentItem(), event.getRawSlot());
        }
    }

    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        Player player = event.getPlayer();
        BedWarsGame game = plugin.getArenaManager().getGameOf(player.getUniqueId());
        if (game == null || game.getState() != BedWarsGame.GameState.RUNNING) return;
        event.setCancelled(true);
        plugin.getShopGui().open(player);
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof Sign sign)) return;

        String line1 = org.bukkit.ChatColor.stripColor(sign.getSide(Side.FRONT).getLine(0)).trim();
        if (!line1.equalsIgnoreCase("[BedWars]")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (plugin.getArenaManager().getGameOf(player.getUniqueId()) != null) {
            player.sendMessage("§cDu bist bereits in einem Spiel.");
            return;
        }

        String arenaName = org.bukkit.ChatColor.stripColor(sign.getSide(Side.FRONT).getLine(1)).trim();
        BedWarsGame game = arenaName.isEmpty()
                ? plugin.getArenaManager().findOrCreateAnyGame()
                : plugin.getArenaManager().findOrCreateGame(arenaName);

        if (game == null) { player.sendMessage("§cKeine Arena verfügbar."); return; }
        if (!game.addPlayer(player)) player.sendMessage("§cArena voll oder Spiel läuft bereits.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BedWarsGame game = plugin.getArenaManager().getGameOf(player.getUniqueId());
        if (game != null) {
            game.removePlayer(player, true);
        }
    }
}
