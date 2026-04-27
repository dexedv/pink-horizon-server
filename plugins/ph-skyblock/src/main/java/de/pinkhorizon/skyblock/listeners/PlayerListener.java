package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import de.pinkhorizon.skyblock.enums.TitleType;
import de.pinkhorizon.skyblock.gui.NavigatorGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class PlayerListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** PDC-Key zum Erkennen des Navigator-Items */
    static final NamespacedKey NAV_KEY = new NamespacedKey("ph_skyblock", "navigator");

    /** Hotbar-Slot des Navigators (0-basiert → Slot 9 = Index 8) */
    private static final int NAV_SLOT = 8;

    private final PHSkyBlock plugin;

    public PlayerListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    // ── Join / Quit ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        var player = e.getPlayer();
        SkyPlayer sp = plugin.getPlayerManager().loadPlayer(player.getUniqueId(), player.getName());

        // Insel laden (wenn vorhanden, in Cache)
        if (sp.getIslandId() != null) {
            Island island = plugin.getIslandManager().getIslandById(sp.getIslandId());
            if (island == null) {
                sp.setIslandId(null);
                plugin.getIslandRepository().setPlayerIslandId(player.getUniqueId(), null);
            }
        }

        // Erweiterte Systeme laden
        plugin.getGeneratorRepository().ensurePlayerExt(player.getUniqueId());
        plugin.getAchievementManager().loadPlayer(player.getUniqueId());
        plugin.getTitleManager().loadPlayer(player.getUniqueId());
        plugin.getQuestManager().loadPlayer(player.getUniqueId());
        plugin.getGeneratorManager().loadForPlayer(player.getUniqueId());

        // Inselbesitzer-Titel sicherstellen (falls vorhanden aber noch kein Titel aktiv)
        if (sp.getIslandId() != null) {
            plugin.getAchievementManager().grantTitleOwnership(player.getUniqueId(), TitleType.INSELBESITZER);
            if (plugin.getTitleManager().getActiveTitle(player.getUniqueId()) == TitleType.KEIN_TITEL) {
                plugin.getTitleManager().silentSetActiveTitle(player.getUniqueId(), TitleType.INSELBESITZER);
            }
        }

        // Gamemode setzen
        applyGameMode(player);

        // Scoreboard zeigen
        plugin.getScoreboardManager().show(player);

        // Tablist setzen
        updateTablist(player);

        // Navigator-Item in letzten Hotbar-Slot
        plugin.getServer().getScheduler().runTask(plugin, () -> giveNavigator(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        var player = e.getPlayer();
        plugin.getScoreboardManager().remove(player);
        plugin.getQuestManager().savePlayer(player.getUniqueId());
        plugin.getGeneratorManager().saveAndUnloadForPlayer(player.getUniqueId());
        plugin.getAchievementManager().unloadPlayer(player.getUniqueId());
        plugin.getTitleManager().unloadPlayer(player.getUniqueId());
        plugin.getPlayerManager().saveAndUnload(player.getUniqueId());
    }

    // ── Welt-Wechsel / Respawn ────────────────────────────────────────────────

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        applyGameMode(e.getPlayer());
        updateTablist(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        var player = e.getPlayer();
        var skyWorld = plugin.getWorldManager().getSkyblockWorld();
        // Bei Tod in der Skyblock-Welt → Respawn am Spawn
        if (skyWorld != null && player.getWorld().equals(skyWorld)) {
            var spawnCfg = plugin.getConfig().getConfigurationSection("spawn");
            if (spawnCfg != null) {
                var spawnWorld = org.bukkit.Bukkit.getWorld(
                    spawnCfg.getString("world", "world"));
                if (spawnWorld == null) spawnWorld = plugin.getWorldManager().getLobbyWorld();
                if (spawnWorld != null) {
                    e.setRespawnLocation(new org.bukkit.Location(
                        spawnWorld,
                        spawnCfg.getDouble("x", 0.5),
                        spawnCfg.getDouble("y", 65.0),
                        spawnCfg.getDouble("z", 0.5)
                    ));
                }
            }
        }
        // Navigator nach Respawn wiedergeben
        plugin.getServer().getScheduler().runTask(plugin, () -> giveNavigator(player));
    }

    // ── Navigator: Drop verhindern ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (isNavigator(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    // ── Navigator: Beim Tod nicht fallen lassen ───────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isNavigator);
    }

    // ── Navigator: Im Inventar nicht verschieben ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (isNavigator(e.getCurrentItem()) || isNavigator(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    // ── Navigator: Rechtsklick → Menü öffnen ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        var action = e.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (!isNavigator(e.getItem())) return;
        e.setCancelled(true);
        new NavigatorGui(plugin, e.getPlayer()).open(e.getPlayer());
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private void giveNavigator(Player player) {
        // Slot 8 bereits mit Navigator belegt? → nichts tun
        ItemStack existing = player.getInventory().getItem(NAV_SLOT);
        if (isNavigator(existing)) return;

        // Vorhandenes Item aus Slot 8 in erstes freies Slot verschieben
        if (existing != null && existing.getType() != Material.AIR) {
            var overflow = player.getInventory().addItem(existing);
            overflow.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }

        ItemStack nav = new ItemStack(Material.COMPASS);
        ItemMeta meta = nav.getItemMeta();
        meta.displayName(MM.deserialize(
            "<gradient:#ff69b4:#da70d6><bold>⭐ Navigator</bold></gradient>").decoration(
            net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
            MM.deserialize("<gray>Rechtsklick zum Öffnen").decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
            MM.deserialize("<dark_gray>• Kann nicht gedroppt werden").decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        meta.setCustomModelData(1001);
        meta.getPersistentDataContainer().set(NAV_KEY, PersistentDataType.BYTE, (byte) 1);
        nav.setItemMeta(meta);
        player.getInventory().setItem(NAV_SLOT, nav);
    }

    boolean isNavigator(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(NAV_KEY, PersistentDataType.BYTE);
    }

    private void applyGameMode(Player player) {
        World lobby = plugin.getWorldManager().getLobbyWorld();
        World sky   = plugin.getWorldManager().getSkyblockWorld();
        if (lobby != null && player.getWorld().equals(lobby)) {
            player.setGameMode(GameMode.ADVENTURE);
        } else if (sky != null && player.getWorld().equals(sky)) {
            if (player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.CREATIVE) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    private void updateTablist(Player player) {
        SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        Island island = (sp != null && sp.getIslandId() != null)
            ? plugin.getIslandManager().getIslandById(sp.getIslandId())
            : null;

        Component header = MM.deserialize(
            "\n<gradient:#ff69b4:#da70d6><bold>✦ Pink Horizon – SkyBlock ✦</bold></gradient>\n"
        );

        String inselInfo = island != null
            ? "<gray>Insel-Level: <gold>" + island.getLevel()
              + "  <gray>Score: <white>" + island.getScore()
            : "<gray>Noch keine Insel – <yellow>/is create";

        Component footer = MM.deserialize(
            "\n" + inselInfo + "\n"
            + "<gray>Online: <green>" + org.bukkit.Bukkit.getOnlinePlayers().size()
            + "  <gray>Server: <light_purple>play.pinkhorizon.fun\n"
        );

        player.sendPlayerListHeaderAndFooter(header, footer);
    }
}
