package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.PlayerQuest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Zeigt die drei täglichen Quests des Spielers.
 * Quests können eingelöst werden wenn abgeschlossen.
 */
public class QuestGui extends GuiBase {

    private final PHSkyBlock plugin;
    private final Player player;
    private List<PlayerQuest> quests;

    // Slots für die drei Quest-Karten
    private static final int[] QUEST_SLOTS = {20, 22, 24};
    private static final int SLOT_CLOSE = 26;
    private static final int SLOT_INFO  = 4;

    public QuestGui(PHSkyBlock plugin, Player player) {
        super("<light_purple>✦ Tägliche Quests", 3);
        this.plugin = plugin;
        this.player = player;
        this.quests = plugin.getQuestManager().getQuests(player.getUniqueId());
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.PURPLE_STAINED_GLASS_PANE);

        // ── Reset-Info ────────────────────────────────────────────────────────
        long hoursLeft = ChronoUnit.HOURS.between(
            LocalDateTime.now(),
            LocalDate.now().plusDays(1).atTime(LocalTime.MIDNIGHT)
        );
        inventory.setItem(SLOT_INFO, item(
            Material.CLOCK,
            "<light_purple><bold>Tägliche Quests",
            "<gray>Quests setzen täglich um <white>00:00 Uhr<gray> zurück.",
            "<gray>Noch ca. <yellow>" + hoursLeft + "h<gray> bis zum Reset.",
            " ",
            "<gray>Abgeschlossene Quests einlösen für:",
            "<gold>Coins <gray>+ <green>Insel-Score!"
        ));

        // ── Quest-Karten ──────────────────────────────────────────────────────
        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            if (i < quests.size()) {
                inventory.setItem(QUEST_SLOTS[i], buildQuestItem(quests.get(i)));
            } else {
                inventory.setItem(QUEST_SLOTS[i],
                    item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Keine Quest"));
            }
        }

        // ── Schließen ─────────────────────────────────────────────────────────
        inventory.setItem(SLOT_CLOSE, closeButton());
    }

    private org.bukkit.inventory.ItemStack buildQuestItem(PlayerQuest q) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + q.getType().getDescription().replace("{goal}",
            String.format("%,d", q.getGoal())));
        lore.add(" ");
        lore.add("<gray>Schwierigkeit: " + q.getDiffName());
        lore.add("<gray>Fortschritt: <white>" + String.format("%,d", q.getProgress())
            + " / " + String.format("%,d", q.getGoal()));
        lore.add(q.getProgressBar());
        lore.add(" ");
        lore.add("<gold>Belohnung: <yellow>" + String.format("%,d", q.getReward()) + " Coins");
        lore.add("<green>+<white>" + q.getScoreReward() + " <green>Insel-Score");
        lore.add(" ");

        Material mat;
        String title;
        if (q.isRewardClaimed()) {
            mat = Material.LIME_STAINED_GLASS_PANE;
            title = "<green>✔ <white>" + q.getType().getName();
            lore.add("<green>✔ Belohnung bereits eingelöst.");
        } else if (q.isCompleted()) {
            mat = Material.YELLOW_STAINED_GLASS_PANE;
            title = "<yellow>★ <white>" + q.getType().getName();
            lore.add("<yellow>Klicke um die Belohnung einzulösen!");
        } else {
            mat = q.getType().getIcon();
            title = q.getType().getName();
            lore.add("<gray>Noch nicht abgeschlossen.");
        }

        return item(mat, title, lore.toArray(new String[0]));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            if (slot == QUEST_SLOTS[i] && i < quests.size()) {
                PlayerQuest q = quests.get(i);
                if (q.isCompleted() && !q.isRewardClaimed()) {
                    plugin.getQuestManager().claimReward(player, q);
                    build(); // Refresh
                }
                return;
            }
        }

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }
}
