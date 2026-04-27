package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Generator;
import de.pinkhorizon.skyblock.gui.GeneratorGui;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import de.pinkhorizon.skyblock.managers.GeneratorManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Behandelt Generator-Platzierung, Abbau und Interaktion.
 */
public class GeneratorListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;

    public GeneratorListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    // ── Block platzieren ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        var item = event.getItemInHand();

        if (!plugin.getGeneratorManager().isGeneratorItem(item)) return;

        // Nur in der Skyblock-Welt
        var skyWorld = BentoBoxHook.getSkyBlockWorld().orElse(null);
        if (skyWorld == null || !player.getWorld().equals(skyWorld)) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Generatoren können nur in der SkyBlock-Welt platziert werden!"));
            event.setCancelled(true);
            return;
        }

        // Insel-Check: nur auf eigener Insel
        if (!isOnOwnIsland(player, event.getBlock())) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Du kannst Generatoren nur auf deiner eigenen Insel platzieren!"));
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlock();
        Generator gen = plugin.getGeneratorManager().placeGenerator(
            player.getUniqueId(), block.getLocation());

        if (gen == null) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Generator konnte nicht platziert werden."));
            return;
        }

        // Achievement: erster Generator
        plugin.getAchievementManager().onFirstGenerator(player);

        // Quest: Blöcke platzieren
        plugin.getQuestManager().onBlockPlace(player.getUniqueId());

        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<green>Generator platziert! <gray>Rechtsklick zum Verwalten."));
    }

    // ── Block abbauen ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        Generator gen = plugin.getGeneratorManager().getGeneratorAt(block.getLocation());
        if (gen == null) return;

        // Nur Eigentümer kann abbauen
        if (!gen.getOwnerUuid().equals(player.getUniqueId()) && !player.isOp()) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Das ist nicht dein Generator!"));
            event.setCancelled(true);
            return;
        }

        event.setDropItems(false); // Verhindert standard Drops

        // Puffer verkaufen oder droppen
        if (!gen.getBuffer().isEmpty()) {
            long autoSellCoins = gen.calcAutoSellCoins();
            gen.collectAll().forEach(i ->
                player.getWorld().dropItemNaturally(block.getLocation(), i));
            if (gen.isAutoSell() && autoSellCoins > 0) {
                plugin.getCoinManager().addCoins(player.getUniqueId(), autoSellCoins);
            }
        }

        // Generator-Item zurückgeben
        block.getWorld().dropItemNaturally(block.getLocation(),
            plugin.getGeneratorManager().createGeneratorItem());

        // Generator entfernen
        plugin.getGeneratorManager().removeGenerator(
            block.getWorld().getName(), block.getX(), block.getY(), block.getZ());

        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<yellow>Generator abgebaut. Das Item wurde gedroppt."));
    }

    // ── Rechtsklick → GUI öffnen ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();

        Generator gen = plugin.getGeneratorManager().getGeneratorAt(block.getLocation());
        if (gen == null) return;

        event.setCancelled(true);

        // Nur der Besitzer kann das GUI öffnen (Besuchende sehen Info)
        if (!gen.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<gray>Das ist der Generator von <light_purple>"
                + plugin.getServer().getOfflinePlayer(gen.getOwnerUuid()).getName()
                + "<gray>. (Level " + gen.getLevel() + ")"));
            return;
        }

        new GeneratorGui(plugin, player, gen).open(player);
    }

    // ── Block-Abbau Tracking (für Quests + Achievements) ─────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakMonitor(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material mat = block.getType();

        // Kein Tracking wenn es ein Generator war (wird oben behandelt)
        if (plugin.getGeneratorManager().getGeneratorAt(block.getLocation()) != null) return;

        // Quest-Tracking
        plugin.getQuestManager().onBlockMine(player.getUniqueId(), mat);

        // Mined-Counter erhöhen
        plugin.getGeneratorRepository().addMined(player.getUniqueId(), 1);

        // Achievement-Check
        plugin.getAchievementManager().checkMiningAchievements(player);
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private boolean isOnOwnIsland(Player player, Block block) {
        // BentoBox prüft ob der Block auf der eigenen Insel liegt
        return BentoBoxHook.getIsland(player.getUniqueId())
            .map(island -> island.onIsland(block.getLocation()))
            .orElse(false);
    }
}
