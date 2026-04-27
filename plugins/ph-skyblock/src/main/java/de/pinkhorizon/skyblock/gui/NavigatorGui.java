package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Navigator-Menü (Slot 8 im Hotbar, immer verfügbar).
 * Erlaubt schnellen Zugriff auf Spawn, eigene Insel, Quests und Achievements.
 */
public class NavigatorGui extends GuiBase {

    // Aktions-Slots
    private static final int SLOT_SPAWN        = 10;
    private static final int SLOT_ISLAND       = 12;
    private static final int SLOT_QUESTS       = 14;
    private static final int SLOT_ACHIEVEMENTS = 16;
    private static final int SLOT_CLOSE        = 22;

    private final PHSkyBlock plugin;
    private final Player player;

    public NavigatorGui(PHSkyBlock plugin, Player player) {
        super("<gradient:#ff69b4:#da70d6><bold>⭐ Navigator</bold></gradient>", 3);
        this.plugin = plugin;
        this.player = player;
        build();
    }

    private void build() {
        setBorder(Material.PINK_STAINED_GLASS_PANE);

        SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        Island island = (sp != null && sp.getIslandId() != null)
            ? plugin.getIslandManager().getIslandById(sp.getIslandId())
            : null;

        // Spawn
        inventory.setItem(SLOT_SPAWN, item(Material.COMPASS,
            "<white><bold>Zum Spawn",
            "<gray>Teleportiert dich zum",
            "<gray>Netzwerk-Spawn.",
            "",
            "<yellow>» Klicken zum Teleportieren"));

        // Eigene Insel
        if (island != null) {
            inventory.setItem(SLOT_ISLAND, item(Material.GRASS_BLOCK,
                "<green><bold>Eigene Insel",
                "<gray>Teleportiert dich zu",
                "<gray>deinem Insel-Home.",
                "",
                "<gray>Level: <gold>" + island.getLevel() + "  <gray>Score: <white>" + island.getScore(),
                "",
                "<yellow>» Klicken zum Teleportieren"));
        } else {
            inventory.setItem(SLOT_ISLAND, item(Material.DIRT,
                "<gray><bold>Keine Insel",
                "<gray>Du hast noch keine Insel.",
                "",
                "<yellow>» /is create"));
        }

        // Quests
        inventory.setItem(SLOT_QUESTS, item(Material.BOOK,
            "<aqua><bold>Quests",
            "<gray>Zeige deine täglichen",
            "<gray>Quests und Belohnungen.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        // Achievements
        inventory.setItem(SLOT_ACHIEVEMENTS, item(Material.NETHER_STAR,
            "<light_purple><bold>Achievements",
            "<gray>Zeige alle Errungenschaften",
            "<gray>und deinen Fortschritt.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        // Close
        inventory.setItem(SLOT_CLOSE, closeButton());

        fillEmpty();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_SPAWN -> {
                player.closeInventory();
                teleportToSpawn();
            }
            case SLOT_ISLAND -> {
                player.closeInventory();
                teleportToIsland();
            }
            case SLOT_QUESTS -> {
                plugin.getQuestManager().loadPlayer(player.getUniqueId());
                new QuestGui(plugin, player).open(player);
            }
            case SLOT_ACHIEVEMENTS -> {
                new AchievementGui(plugin, player).open(player);
            }
            case SLOT_CLOSE -> player.closeInventory();
        }
    }

    private void teleportToSpawn() {
        ConfigurationSection spawnCfg = plugin.getConfig().getConfigurationSection("spawn");
        if (spawnCfg == null) return;
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(spawnCfg.getString("world", "world"));
        if (world == null) world = plugin.getWorldManager().getLobbyWorld();
        if (world == null) return;
        player.teleport(new Location(
            world,
            spawnCfg.getDouble("x", 0.5),
            spawnCfg.getDouble("y", 65.0),
            spawnCfg.getDouble("z", 0.5),
            (float) spawnCfg.getDouble("yaw", 0.0),
            (float) spawnCfg.getDouble("pitch", 0.0)
        ));
        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<green>Du wurdest zum Spawn teleportiert."));
    }

    private void teleportToIsland() {
        SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null || sp.getIslandId() == null) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Du hast noch keine Insel! Erstelle eine mit <yellow>/is create</yellow>."));
            return;
        }
        Island island = plugin.getIslandManager().getIslandById(sp.getIslandId());
        if (island == null) return;
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(island.getWorld());
        if (world == null) world = plugin.getWorldManager().getSkyblockWorld();
        if (world == null) return;
        player.teleport(new Location(
            world,
            island.getHomeX(), island.getHomeY(), island.getHomeZ(),
            island.getHomeYaw(), island.getHomePitch()
        ));
        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<green>Du wurdest zu deiner Insel teleportiert."));
    }
}
