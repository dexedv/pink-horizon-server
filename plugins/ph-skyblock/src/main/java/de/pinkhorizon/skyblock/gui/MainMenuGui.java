package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Generator;
import de.pinkhorizon.skyblock.enums.AchievementType;
import de.pinkhorizon.skyblock.enums.TitleType;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Hauptmenü – geöffnet über /phsk oder NPC.
 * Zeigt Insel-Infos (via BentoBox), Generatoren, Quests, Achievements, Titel, Coins.
 */
public class MainMenuGui extends GuiBase {

    private static final int SLOT_ISLAND       = 10;
    private static final int SLOT_GENERATORS   = 11;
    private static final int SLOT_SHOP         = 12;
    private static final int SLOT_QUESTS       = 13;
    private static final int SLOT_ACHIEVEMENTS = 15;
    private static final int SLOT_TITLES       = 16;
    private static final int SLOT_BALANCE      = 22;
    private static final int SLOT_CLOSE        = 26;

    private final PHSkyBlock plugin;
    private final Player player;

    public MainMenuGui(PHSkyBlock plugin, Player player) {
        super("<light_purple>✦ Pink Horizon – SkyBlock", 3);
        this.plugin = plugin;
        this.player = player;
        build();
    }

    private void build() {
        inventory.clear();
        setBorder(Material.PINK_STAINED_GLASS_PANE);

        var uuid      = player.getUniqueId();
        long coins    = plugin.getCoinManager().getCoins(uuid);
        TitleType title = plugin.getTitleManager().getActiveTitle(uuid);
        List<Generator> gens = plugin.getGeneratorManager().getGeneratorsOf(uuid);

        // Insel-Info via BentoBox
        boolean hasIsland = BentoBoxHook.hasIsland(uuid);
        long level        = BentoBoxHook.getIslandLevel(uuid);
        int size          = BentoBoxHook.getIslandSize(uuid);

        if (hasIsland) {
            inventory.setItem(SLOT_ISLAND, item(
                Material.GRASS_BLOCK,
                "<green><bold>Meine Insel",
                "<gray>Level: <yellow>" + level,
                "<gray>Größe: <white>" + size + "×" + size,
                " ",
                "<gray>Nutze <yellow>/is</yellow> für alle Insel-Optionen.",
                " ",
                "<yellow>Klicke zum Teleportieren."
            ));
        } else {
            inventory.setItem(SLOT_ISLAND, item(
                Material.DIRT,
                "<red>Keine Insel",
                "<gray>Erstelle deine Insel mit",
                "<yellow>/is create"
            ));
        }

        // Generatoren
        int totalBuffer = gens.stream().mapToInt(g -> g.getBuffer().size()).sum();
        List<String> genLore = new ArrayList<>();
        genLore.add("<gray>Platzierte Generatoren: <yellow>" + gens.size());
        genLore.add("<gray>Items im Puffer: <white>" + totalBuffer);
        if (!gens.isEmpty()) {
            int maxLevel = gens.stream().mapToInt(Generator::getLevel).max().orElse(0);
            genLore.add("<gray>Höchstes Level: <gold>" + maxLevel);
        }
        genLore.add(" ");
        genLore.add("<yellow>Klicke für Generator-Übersicht.");
        inventory.setItem(SLOT_GENERATORS, item(
            Material.FURNACE, "<gold><bold>⚙ Generatoren",
            genLore.toArray(new String[0])
        ));

        // Shop
        inventory.setItem(SLOT_SHOP, item(
            Material.GOLD_INGOT,
            "<yellow><bold>⚑ Shop",
            "<gray>Kaufe und verkaufe Items",
            "<gray>gegen Coins.",
            " ",
            "<yellow>Klicke zum Öffnen."
        ));

        // Quests
        List<de.pinkhorizon.skyblock.data.PlayerQuest> quests =
            plugin.getQuestManager().getQuests(uuid);
        long claimable = quests.stream().filter(q -> q.isCompleted() && !q.isRewardClaimed()).count();
        inventory.setItem(SLOT_QUESTS, item(
            Material.PAPER,
            "<light_purple><bold>✦ Tägliche Quests",
            "<gray>Aktive Quests: <white>" + quests.size(),
            "<gray>Einlösbar: " + (claimable > 0 ? "<gold>" + claimable : "<gray>0"),
            " ",
            "<yellow>Klicke für Quests."
        ));

        // Achievements
        int unlockedCount = (int) plugin.getAchievementManager().getUnlocked(uuid)
            .stream().filter(id -> !id.startsWith("title_")).count();
        int totalAchievements = AchievementType.values().length;
        inventory.setItem(SLOT_ACHIEVEMENTS, item(
            Material.NETHER_STAR,
            "<gold><bold>★ Achievements",
            "<gray>Freigeschaltet: <yellow>" + unlockedCount + " / " + totalAchievements,
            " ",
            "<yellow>Klicke für Achievements."
        ));

        // Titel
        inventory.setItem(SLOT_TITLES, item(
            Material.NAME_TAG,
            "<aqua><bold>✦ Titel",
            "<gray>Aktiver Titel: " + (title == TitleType.KEIN_TITEL
                ? "<gray>Kein Titel" : title.getChatPrefix().trim().replaceAll("§[0-9a-fA-Fk-oK-OrR]", "")),
            " ",
            "<yellow>Klicke für Titel-Auswahl."
        ));

        // Coins
        inventory.setItem(SLOT_BALANCE, item(
            Material.GOLD_INGOT,
            "<gold><bold>Meine Coins",
            "<gray>Kontostand: <yellow><bold>" + String.format("%,d", coins) + " Coins",
            " ",
            "<gray>Verdiene Coins durch:",
            "<gray>Auto-Sell, Quests & Achievements."
        ));

        inventory.setItem(SLOT_CLOSE, closeButton());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case SLOT_ISLAND -> {
                player.closeInventory();
                BentoBoxHook.teleportHome(player);
            }
            case SLOT_SHOP         -> new ShopGui(plugin, player).open(player);
            case SLOT_QUESTS       -> new QuestGui(plugin, player).open(player);
            case SLOT_ACHIEVEMENTS -> new AchievementGui(plugin, player).open(player);
            case SLOT_TITLES       -> new TitleGui(plugin, player).open(player);
            case SLOT_BALANCE -> {
                plugin.getCoinManager().sendBalance(player);
                player.closeInventory();
            }
            case SLOT_GENERATORS -> {
                List<Generator> gens = plugin.getGeneratorManager().getGeneratorsOf(player.getUniqueId());
                if (gens.isEmpty()) {
                    player.sendMessage(MM.deserialize(
                        "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                        + "<gray>Du hast noch keine Generatoren. Platziere einen Furnace!"));
                    player.closeInventory();
                } else {
                    new GeneratorGui(plugin, player, gens.get(0)).open(player);
                }
            }
            case SLOT_CLOSE -> player.closeInventory();
        }
    }
}
