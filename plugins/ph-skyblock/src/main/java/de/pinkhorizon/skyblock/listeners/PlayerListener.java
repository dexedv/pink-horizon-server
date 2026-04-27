package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.enums.TitleType;
import de.pinkhorizon.skyblock.gui.NavigatorGui;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
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
    private static final int NAV_SLOT = 8;

    private final PHSkyBlock plugin;

    public PlayerListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    // ── Join / Quit ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        var player = e.getPlayer();
        var uuid   = player.getUniqueId();

        plugin.getPlayerManager().loadPlayer(uuid, player.getName());
        plugin.getGeneratorRepository().ensurePlayerExt(uuid);
        plugin.getAchievementManager().loadPlayer(uuid);
        plugin.getTitleManager().loadPlayer(uuid);
        plugin.getQuestManager().loadPlayer(uuid);
        plugin.getGeneratorManager().loadForPlayer(uuid);

        // Inselbesitzer-Titel sicherstellen (via BentoBox)
        if (BentoBoxHook.hasIsland(uuid)) {
            plugin.getAchievementManager().grantTitleOwnership(uuid, TitleType.INSELBESITZER);
            if (plugin.getTitleManager().getActiveTitle(uuid) == TitleType.KEIN_TITEL) {
                plugin.getTitleManager().silentSetActiveTitle(uuid, TitleType.INSELBESITZER);
            }
        }

        applyGameMode(player);
        plugin.getScoreboardManager().show(player);
        updateTablist(player);
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
        plugin.getServer().getScheduler().runTask(plugin, () -> giveNavigator(e.getPlayer()));
    }

    // ── Navigator: Drop verhindern ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (isNavigator(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isNavigator);
    }

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
        ItemStack existing = player.getInventory().getItem(NAV_SLOT);
        // Already up-to-date navigator → skip
        if (isNavigator(existing) && existing.getType() == Material.NETHER_STAR) return;
        // Remove old compass-based navigator if present
        if (isNavigator(existing)) player.getInventory().setItem(NAV_SLOT, null);

        if (existing != null && existing.getType() != Material.AIR) {
            var overflow = player.getInventory().addItem(existing);
            overflow.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }

        ItemStack nav = new ItemStack(Material.NETHER_STAR);
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
        meta.setCustomModelData(2099);
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
        // Lobby-Welt = Adventure, BSkyBlock-Welt = Survival
        var skyWorld = BentoBoxHook.getSkyBlockWorld().orElse(null);
        if (skyWorld != null && player.getWorld().equals(skyWorld)) {
            if (player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.CREATIVE) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    private void updateTablist(Player player) {
        boolean hasIsland = BentoBoxHook.hasIsland(player.getUniqueId());
        long level        = BentoBoxHook.getIslandLevel(player.getUniqueId());

        Component header = MM.deserialize(
            "\n<gradient:#ff69b4:#da70d6><bold>✦ Pink Horizon – SkyBlock ✦</bold></gradient>\n"
        );

        String inselInfo = hasIsland
            ? "<gray>Insel-Level: <gold>" + level
            : "<gray>Noch keine Insel – <yellow>/is create";

        Component footer = MM.deserialize(
            "\n" + inselInfo + "\n"
            + "<gray>Online: <green>" + org.bukkit.Bukkit.getOnlinePlayers().size()
            + "  <gray>Server: <light_purple>play.pinkhorizon.fun\n"
        );

        player.sendPlayerListHeaderAndFooter(header, footer);
    }
}
