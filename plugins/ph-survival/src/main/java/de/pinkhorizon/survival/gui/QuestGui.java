package de.pinkhorizon.survival.gui;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.QuestManager;
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

public class QuestGui implements Listener {

    private static final String TITLE  = "§e§lTägliche Quests";
    private static final long   REWARD = 300L;

    private final PHSurvival plugin;

    public QuestGui(PHSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Öffnen ───────────────────────────────────────────────────────────

    public void open(Player player) {
        List<QuestManager.Quest> quests =
                plugin.getQuestManager().getDailyQuests(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Überschrift
        inv.setItem(4, make(Material.PAPER,
                txt("§e§lTägliche Quests", NamedTextColor.YELLOW),
                List.of(
                    txt("3 Quests · Reset täglich um Mitternacht", NamedTextColor.GRAY),
                    empty(),
                    txt("Belohnung: §6" + REWARD + " Coins §7pro Quest", NamedTextColor.WHITE)
                )));

        // Quest-Slots: 11, 13, 15
        int[] slots = {11, 13, 15};
        for (int i = 0; i < quests.size() && i < slots.length; i++) {
            inv.setItem(slots[i], buildQuestItem(quests.get(i)));
        }

        // Fortschritts-Zusammenfassung unten
        long done = quests.stream().filter(QuestManager.Quest::completed).count();
        String prog = (int) done + "/" + quests.size() + " abgeschlossen";
        inv.setItem(22, make(Material.GOLD_INGOT,
                txt("Fortschritt", NamedTextColor.GOLD),
                List.of(
                    txt(prog, done == quests.size() ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                    empty(),
                    txt("Verdient: §6" + (done * REWARD) + " Coins", NamedTextColor.WHITE)
                )));

        fillGlass(inv);
        player.openInventory(inv);
    }

    // ── Quest-Item aufbauen ───────────────────────────────────────────────

    private ItemStack buildQuestItem(QuestManager.Quest quest) {
        Material mat = iconFor(quest.type());
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        if (quest.completed()) {
            meta.displayName(Component.text("✔ " + quest.type().description, NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            meta.setEnchantmentGlintOverride(true);
        } else {
            meta.displayName(Component.text("» " + quest.type().description, NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(empty());

        // Fortschrittsbalken
        int progress = quest.progress();
        int goal     = quest.type().goal;
        lore.add(progressBar(progress, goal));
        lore.add(txt("Fortschritt: " + progress + " / " + goal, NamedTextColor.GRAY));
        lore.add(empty());

        if (quest.completed()) {
            lore.add(txt("§a✔ Belohnung erhalten: §6" + REWARD + " Coins", NamedTextColor.WHITE));
        } else {
            int remaining = goal - progress;
            lore.add(txt("Noch " + remaining + " bis zum Ziel", NamedTextColor.GRAY));
            lore.add(txt("Belohnung: §6" + REWARD + " Coins", NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component progressBar(int progress, int goal) {
        int total  = 20;
        int filled = (int) Math.round(total * (double) progress / goal);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            sb.append(i < filled ? "§a" : "§8");
            sb.append("█");
        }
        int pct = (int) (100.0 * progress / goal);
        return Component.text(sb + " §7" + pct + "%").decoration(TextDecoration.ITALIC, false);
    }

    private static Material iconFor(QuestManager.QuestType type) {
        return switch (type) {
            case MINE_ORES     -> Material.DIAMOND_PICKAXE;
            case CUT_TREES     -> Material.IRON_AXE;
            case KILL_MOBS     -> Material.BOW;
            case CATCH_FISH    -> Material.FISHING_ROD;
            case HARVEST_CROPS -> Material.WHEAT;
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
