package de.pinkhorizon.battlepass.gui;

import de.pinkhorizon.battlepass.ChallengeType;
import de.pinkhorizon.battlepass.PHBattlePass;
import de.pinkhorizon.battlepass.managers.BattlePassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI: Battle Pass Übersicht – Level, XP-Balken, Challenges per Periode.
 * 5 Reihen.
 */
public class BattlePassGui extends GuiBase {

    private static final int SLOT_STATUS   = 4;
    private static final int SLOT_DAILY    = 11;
    private static final int SLOT_WEEKLY   = 13;
    private static final int SLOT_SEASONAL = 15;
    private static final int SLOT_CLAIM    = 22;
    private static final int SLOT_CLOSE    = 40;

    private final PHBattlePass plugin;
    private final Player player;
    private final BattlePassManager manager;

    public BattlePassGui(PHBattlePass plugin, Player player) {
        super("<gold>⭐ <bold>Battle Pass</bold>", 5);
        this.plugin  = plugin;
        this.player  = player;
        this.manager = plugin.getBattlePassManager();
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.YELLOW_STAINED_GLASS_PANE);

        UUID uuid      = player.getUniqueId();
        int level      = manager.getLevel(uuid);
        long xp        = manager.getXp(uuid);
        boolean prem   = manager.isPremium(uuid);
        int maxLevel   = plugin.getConfig().getInt("max-level", 100);
        int xpPerLevel = plugin.getConfig().getInt("xp-per-level", 1000);
        long xpInLevel = xp % xpPerLevel;

        int filled = (int) ((double) xpInLevel / xpPerLevel * 10);
        String bar = "<green>" + "█".repeat(filled) + "<dark_gray>" + "█".repeat(10 - filled);

        inventory.setItem(SLOT_STATUS, item(
            prem ? Material.NETHER_STAR : Material.PAPER,
            (prem ? "<gold>" : "<gray>") + "<bold>Battle Pass",
            "<gray>Season: <white>" + plugin.getConfig().getString("season-name", "Season 1"),
            "<gray>Level: <white>" + level + " <dark_gray>/ " + maxLevel,
            "<gray>XP: <white>" + xpInLevel + " <dark_gray>/ " + xpPerLevel,
            "<gray>Typ: " + (prem ? "<gold>Premium ★" : "<gray>Gratis"),
            "",
            "[" + bar + "<gray>]"
        ));

        String today = LocalDate.now().toString();
        String week  = LocalDate.now().with(DayOfWeek.MONDAY).toString();
        Map<String, Integer> prog = manager.getChallengeProgress(uuid);

        inventory.setItem(SLOT_DAILY,    buildChallengesItem(Material.SUNFLOWER,
            "<yellow><bold>Tägliche Challenges", "DAILY", today, prog));
        inventory.setItem(SLOT_WEEKLY,   buildChallengesItem(Material.CLOCK,
            "<green><bold>Wöchentliche Challenges", "WEEKLY", week, prog));
        inventory.setItem(SLOT_SEASONAL, buildChallengesItem(Material.NETHER_STAR,
            "<light_purple><bold>Saisonale Challenges", "SEASONAL", "SEASONAL", prog));

        inventory.setItem(SLOT_CLAIM, item(Material.CHEST,
            "<gold><bold>Belohnungen einlösen",
            "<gray>Aktuelles Level: <white>" + level,
            "",
            "<yellow>Nutze /bp claim <level>",
            "<gray>um Level-Belohnungen einzulösen."
        ));

        inventory.setItem(SLOT_CLOSE, closeButton());
        fillEmpty();
    }

    private ItemStack buildChallengesItem(Material icon, String title,
            String period, String resetKey, Map<String, Integer> progress) {
        ItemStack it  = new ItemStack(icon);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        meta.displayName(MM.deserialize(title).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        long done  = 0;
        long total = 0;

        for (ChallengeType c : ChallengeType.values()) {
            if (!c.period.equals(period)) continue;
            total++;
            String key = c.name() + "_" + resetKey;
            int p = progress.getOrDefault(key, 0);
            boolean completed = p >= c.targetAmount;
            if (completed) done++;

            lore.add(MM.deserialize((completed ? "<dark_gray>✓ " : "<white>○ ")
                + c.displayName
                + " <dark_gray>[" + p + "/" + c.targetAmount + "]"
                + (completed ? "" : " <gold>+" + c.bpXp + " XP"))
                .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(0, MM.deserialize("<gray>Abgeschlossen: <white>" + done + "/" + total)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(1, MM.deserialize("").decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getSlot() == SLOT_CLOSE) p.closeInventory();
    }
}
