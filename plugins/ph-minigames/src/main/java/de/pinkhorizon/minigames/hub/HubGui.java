package de.pinkhorizon.minigames.hub;

import de.pinkhorizon.minigames.PHMinigames;
import de.pinkhorizon.minigames.bedwars.BedWarsGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class HubGui {

    private final PHMinigames plugin;

    public HubGui(PHMinigames plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("✦ Spiele wählen ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));

        // Füllglas
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.displayName(Component.empty());
        glass.setItemMeta(gm);
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // BedWars (Slot 13 = Mitte)
        long waiting = plugin.getArenaManager().getActiveGames().stream()
                .filter(g -> g.getState() == BedWarsGame.GameState.WAITING
                          || g.getState() == BedWarsGame.GameState.STARTING)
                .count();
        long running = plugin.getArenaManager().getActiveGames().stream()
                .filter(g -> g.getState() == BedWarsGame.GameState.RUNNING)
                .count();

        ItemStack bed = new ItemStack(Material.RED_BED);
        ItemMeta bm = bed.getItemMeta();
        bm.displayName(Component.text("BedWars", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Zerstöre die Betten deiner Gegner!", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("▸ Wartend: ", NamedTextColor.YELLOW)
                .append(Component.text(waiting + " Spiel(e)", NamedTextColor.WHITE)));
        lore.add(Component.text("▸ Laufend: ", NamedTextColor.GREEN)
                .append(Component.text(running + " Spiel(e)", NamedTextColor.WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text("» Klicken zum Beitreten! «", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        bm.lore(lore);
        bed.setItemMeta(bm);
        inv.setItem(13, bed);

        player.openInventory(inv);
    }

    public void handleClick(Player player, int rawSlot) {
        if (rawSlot == 13) {
            player.closeInventory();
            BedWarsGame game = plugin.getArenaManager().findOrCreateAnyGame();
            if (game == null) {
                player.sendMessage("§cKeine BedWars-Arena verfügbar.");
                return;
            }
            if (!game.addPlayer(player)) {
                player.sendMessage("§cArena voll oder Spiel läuft bereits.");
            }
        }
    }
}
