package de.pinkhorizon.minigames.hub;

import de.pinkhorizon.minigames.PHMinigames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class HubManager {

    private final PHMinigames plugin;

    public HubManager(PHMinigames plugin) {
        this.plugin = plugin;
    }

    /** Setzt den Spieler in den Hub-Zustand: Adventure, leer, Kompass, Tab. */
    public void setupHubPlayer(Player player) {
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
