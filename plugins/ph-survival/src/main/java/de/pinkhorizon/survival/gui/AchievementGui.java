package de.pinkhorizon.survival.gui;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.AchievementManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AchievementGui implements Listener {

    private static final String TITLE = "§d§lAchievements";

    private final PHSurvival plugin;

    public AchievementGui(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Öffnen ───────────────────────────────────────────────────────────

    public void open(Player player) {
        AchievementManager.Achievement[] all      = AchievementManager.Achievement.values();
        Set<AchievementManager.Achievement> done  = plugin.getAchievementManager().getUnlocked(player.getUniqueId());

        // 15 Achievements in 5er-Spalten, je 3 Reihen → 54-Slot-Inventar
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Achievements in 3 Zeilen à 5, zentriert (Spalten 2-6)
        // Zeile 0: Slots  1- 5
        // Zeile 1: Slots 10-14
        // Zeile 2: Slots 19-23
        int[] achSlots = {1, 2, 3, 4, 5, 10, 11, 12, 13, 14, 19, 20, 21, 22, 23};

        for (int i = 0; i < all.length && i < achSlots.length; i++) {
            inv.setItem(achSlots[i], buildAchItem(all[i], done.contains(all[i])));
        }

        // Statistik-Item (Mitte unten)
        long totalReward = done.stream().mapToLong(a -> a.reward).sum();
        inv.setItem(49, make(Material.NETHER_STAR,
                txt("Fortschritt", NamedTextColor.LIGHT_PURPLE),
                List.of(
                    txt(done.size() + " / " + all.length + " freigeschaltet", NamedTextColor.WHITE),
                    empty(),
                    txt("Verdient: §6" + totalReward + " Coins", NamedTextColor.GRAY),
                    txt("Offen:    §c" + (all.length - done.size()) + " Achievements", NamedTextColor.GRAY)
                )));

        fillGlass(inv);
        player.openInventory(inv);
    }

    // ── Achievement-Item aufbauen ─────────────────────────────────────────

    private ItemStack buildAchItem(AchievementManager.Achievement ach, boolean unlocked) {
        Material mat  = iconFor(ach);
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        if (unlocked) {
            meta.displayName(Component.text("✔ " + ach.displayName, NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            meta.setEnchantmentGlintOverride(true);
        } else {
            meta.displayName(Component.text("✘ " + ach.displayName, NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(empty());

        // Sterne
        lore.add(txt(ach.stars + " " + difficultyLabel(ach.stars),
                unlocked ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));

        lore.add(empty());

        // Beschreibung
        lore.add(txt(ach.description, unlocked ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY));

        lore.add(empty());

        if (unlocked) {
            lore.add(txt("§a✔ Belohnung erhalten: §6" + ach.reward + " Coins", NamedTextColor.WHITE));
        } else {
            lore.add(txt("Belohnung: §6" + ach.reward + " Coins", NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String difficultyLabel(String stars) {
        return switch (stars) {
            case "✦"     -> "Einfach";
            case "✦✦"   -> "Mittel";
            case "✦✦✦" -> "Schwer";
            default -> "";
        };
    }

    private static Material iconFor(AchievementManager.Achievement ach) {
        return switch (ach) {
            case FIRST_STEPS    -> Material.GRASS_BLOCK;
            case COIN_COLLECTOR -> Material.GOLD_NUGGET;
            case COIN_MASTER    -> Material.GOLD_INGOT;
            case RICH           -> Material.GOLD_BLOCK;
            case JOB_BEGINNER   -> Material.COMPASS;
            case JOB_EXPERT     -> Material.BLAZE_ROD;
            case JOB_MASTER     -> Material.NETHER_STAR;
            case HOMEOWNER      -> Material.RED_BED;
            case CLAIMER        -> Material.IRON_SHOVEL;
            case TRADER         -> Material.EMERALD;
            case BANKIER        -> Material.NETHERITE_INGOT;
            case PLAY_1H        -> Material.CLOCK;
            case PLAY_10H       -> Material.DIAMOND;
            case QUEST_DONE     -> Material.BOOK;
            case FRIENDS        -> Material.HEART_OF_THE_SEA;
        };
    }

    // ── Click-Handler ─────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    private ItemStack make(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component txt(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }
    private static Component empty() { return Component.empty(); }

    private void fillGlass(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta  = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }
}
