package de.pinkhorizon.minigames.hub;

import de.pinkhorizon.minigames.PHMinigames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class HubManager {

    private final PHMinigames plugin;

    public HubManager(PHMinigames plugin) {
        this.plugin = plugin;
    }

    /** Setzt den Spieler in den Hub-Zustand: Adventure, leer, Kompass, Tab. Teleportiert zum Hub-Spawn. */
    public void setupHubPlayer(Player player) {
        Location spawn = getHubSpawn();
        if (spawn != null) player.teleport(spawn);
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        player.getInventory().setItem(4, buildCompass());
        setHubTabHeader(player);
    }

    /** Liest den Hub-Spawn aus der config.yml. Gibt null zurück wenn nicht gesetzt. */
    public Location getHubSpawn() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.contains("hub.spawn.world")) return null;
        String worldName = cfg.getString("hub.spawn.world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x   = cfg.getDouble("hub.spawn.x");
        double y   = cfg.getDouble("hub.spawn.y");
        double z   = cfg.getDouble("hub.spawn.z");
        float  yaw   = (float) cfg.getDouble("hub.spawn.yaw");
        float  pitch = (float) cfg.getDouble("hub.spawn.pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** Speichert den Hub-Spawn in der config.yml. */
    public void saveHubSpawn(Location loc) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("hub.spawn.world", loc.getWorld().getName());
        cfg.set("hub.spawn.x",     loc.getX());
        cfg.set("hub.spawn.y",     loc.getY());
        cfg.set("hub.spawn.z",     loc.getZ());
        cfg.set("hub.spawn.yaw",   (double) loc.getYaw());
        cfg.set("hub.spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }

    /** Setzt den Hub-Tab-Header (ohne Inventar anzufassen). */
    public void setHubTabHeader(Player player) {
        player.sendPlayerListHeaderAndFooter(
                Component.text("✦ Pink Horizon Minigames ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Nutze den Kompass, um ein Spiel auszuwählen", NamedTextColor.GRAY)
        );
        player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
    }

    public static ItemStack buildCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.displayName(Component.text("Spiele wählen", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        meta.lore(List.of(Component.text("Rechtsklick zum Öffnen", NamedTextColor.GRAY)));
        compass.setItemMeta(meta);
        return compass;
    }

    public boolean isHubCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        if (!item.hasItemMeta()) return false;
        Component name = item.getItemMeta().displayName();
        if (name == null) return false;
        String plain = PlainTextComponentSerializer.plainText().serialize(name);
        return plain.contains("Spiele wählen");
    }
}
