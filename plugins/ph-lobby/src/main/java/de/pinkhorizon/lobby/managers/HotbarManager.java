package de.pinkhorizon.lobby.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class HotbarManager {

    public static final String KEY_ACTION = "hotbar_action";

    public static void giveHotbar(Player player, JavaPlugin plugin) {
        player.getInventory().clear();

        // Slot 0: Kompass = Navigator
        player.getInventory().setItem(0, buildItem(plugin, Material.COMPASS,
                "\u00a7d\u00a7lNavigator",
                List.of("\u00a77Rechtsklick zum Öffnen", "\u00a77Wechsle den Server"),
                "navigator"));

        // Slot 4: Nether-Stern = Spieler-Liste
        player.getInventory().setItem(4, buildItem(plugin, Material.NETHER_STAR,
                "\u00a7e\u00a7lOnline-Spieler",
                List.of("\u00a77Zeigt alle Online-Spieler"),
                "playerlist"));

        // Slot 8: Buch = Regeln
        player.getInventory().setItem(8, buildItem(plugin, Material.BOOK,
                "\u00a7f\u00a7lServerregeln",
                List.of("\u00a77Klicken zum Lesen"),
                "rules"));
    }

    private static ItemStack buildItem(JavaPlugin plugin, Material mat, String name,
                                        List<String> lore, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(lore.stream().map(s -> Component.text(s, NamedTextColor.GRAY)).toList());
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, KEY_ACTION),
                PersistentDataType.STRING, action);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }
}
