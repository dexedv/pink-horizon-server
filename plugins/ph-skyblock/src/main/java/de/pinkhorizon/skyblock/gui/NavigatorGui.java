package de.pinkhorizon.skyblock.gui;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Navigator-Menü (Slot 8 im Hotbar, immer verfügbar).
 * Bietet schnellen Zugriff auf alle Features von Pink Horizon SkyBlock.
 *
 * Layout (5 Reihen):
 *   Reihe 1 (10-16): Kern-Features
 *   Reihe 2 (19-25): Welt-Features
 *   Reihe 3 (28-34): Externe Plugins
 *   Reihe 4 (36-44): Rahmen + Schließen (40)
 */
public class NavigatorGui extends GuiBase {

    // ── Reihe 1: Kern-Features (Slots 10–16) ──────────────────────────────────
    private static final int SLOT_SPAWN        = 10;
    private static final int SLOT_ISLAND       = 11;
    private static final int SLOT_SHOP         = 12;
    private static final int SLOT_QUESTS       = 13;
    private static final int SLOT_ACHIEVEMENTS = 14;
    private static final int SLOT_TITLES       = 15;
    private static final int SLOT_GENERATORS   = 16;

    // ── Reihe 2: Welt-Features (Slots 19–25) ──────────────────────────────────
    private static final int SLOT_DNA          = 19;
    private static final int SLOT_RITUALS      = 20;
    private static final int SLOT_CONTRACTS    = 21;
    private static final int SLOT_STORY        = 22;
    private static final int SLOT_BLUEPRINTS   = 23;
    private static final int SLOT_VOIDFISHING  = 24;
    private static final int SLOT_CHRONICLE    = 25;

    // ── Reihe 3: Externe Plugins (Slots 28–34) ────────────────────────────────
    private static final int SLOT_COMPANIONS   = 28;
    private static final int SLOT_RUNES        = 29;
    private static final int SLOT_DUNGEONS     = 30;
    private static final int SLOT_BATTLEPASS   = 31;
    private static final int SLOT_WEATHER      = 32;
    private static final int SLOT_STARS        = 33;

    private static final int SLOT_CLOSE        = 40;

    private final PHSkyBlock plugin;
    private final Player player;

    public NavigatorGui(PHSkyBlock plugin, Player player) {
        super("<gradient:#ff69b4:#da70d6><bold>⭐ Navigator</bold></gradient>", 5);
        this.plugin = plugin;
        this.player = player;
        build();
    }

    private void build() {
        setBorder(Material.PINK_STAINED_GLASS_PANE);

        boolean hasIsland = BentoBoxHook.hasIsland(player.getUniqueId());
        long level        = BentoBoxHook.getIslandLevel(player.getUniqueId());
        int size          = BentoBoxHook.getIslandSize(player.getUniqueId());

        // ── Reihe 1 ───────────────────────────────────────────────────────────

        inventory.setItem(SLOT_SPAWN, item(Material.COMPASS,
            "<white><bold>Zum Spawn",
            "<gray>Teleportiert dich zum",
            "<gray>Netzwerk-Spawn.",
            "",
            "<yellow>» Klicken zum Teleportieren"));

        if (hasIsland) {
            inventory.setItem(SLOT_ISLAND, item(Material.GRASS_BLOCK,
                "<green><bold>Eigene Insel",
                "<gray>Teleportiert dich zu",
                "<gray>deinem Insel-Home.",
                "",
                "<gray>Level: <gold>" + level + "  <gray>Größe: <white>" + size + "×" + size,
                "",
                "<yellow>» Klicken zum Teleportieren"));
        } else {
            inventory.setItem(SLOT_ISLAND, item(Material.DIRT,
                "<gray><bold>Keine Insel",
                "<gray>Du hast noch keine Insel.",
                "",
                "<yellow>» /is create"));
        }

        inventory.setItem(SLOT_SHOP, item(Material.GOLD_INGOT,
            "<yellow><bold>⚑ Shop",
            "<gray>Kaufe und verkaufe Items",
            "<gray>gegen Coins.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_QUESTS, item(Material.BOOK,
            "<aqua><bold>Quests",
            "<gray>Tägliche & wöchentliche",
            "<gray>Aufgaben mit Belohnungen.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_ACHIEVEMENTS, item(Material.TOTEM_OF_UNDYING,
            "<light_purple><bold>Achievements",
            "<gray>Errungenschaften und",
            "<gray>dein Fortschritt.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_TITLES, item(Material.NAME_TAG,
            "<gold><bold>Titel",
            "<gray>Verwalte deine Titel",
            "<gray>und aktiviere einen.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_GENERATORS, item(Material.REDSTONE_BLOCK,
            "<red><bold>Generatoren",
            "<gray>IdleForge-Generatoren,",
            "<gray>Prestige und Booster.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        // ── Reihe 2 ───────────────────────────────────────────────────────────

        inventory.setItem(SLOT_DNA, item(Material.AMETHYST_SHARD,
            "<dark_aqua><bold>Insel-DNA",
            "<gray>Die einzigartigen Gene",
            "<gray>deiner Insel.",
            "",
            "<yellow>» Klicken zum Anzeigen"));

        inventory.setItem(SLOT_RITUALS, item(Material.ENDER_EYE,
            "<dark_purple><bold>Rituale",
            "<gray>Aktiviere mächtige",
            "<gray>Insel-Rituale.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_CONTRACTS, item(Material.PAPER,
            "<yellow><bold>Auftrags-Board",
            "<gray>Lieferaufträge annehmen",
            "<gray>und Belohnungen verdienen.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_STORY, item(Material.ENCHANTED_BOOK,
            "<dark_purple><bold>Story & Lore",
            "<gray>Die Geschichte von",
            "<gray>Pink Horizon entfaltet sich.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_BLUEPRINTS, item(Material.FILLED_MAP,
            "<green><bold>Blueprints",
            "<gray>Speichere und lade",
            "<gray>Insel-Layouts.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_VOIDFISHING, item(Material.FISHING_ROD,
            "<blue><bold>Void-Fishing",
            "<gray>Angel in der Leere unter",
            "<gray>deiner Insel (Y < 0).",
            "",
            "<gray>Benutze eine normale Angel",
            "<gray>unterhalb von Y=0.",
            "",
            "<yellow>» Klicken für Info"));

        inventory.setItem(SLOT_CHRONICLE, item(Material.WRITABLE_BOOK,
            "<white><bold>Insel-Chronik",
            "<gray>Die Geschichte deiner",
            "<gray>Insel als Buch.",
            "",
            "<yellow>» Klicken zum Erhalten"));

        // ── Reihe 3 ───────────────────────────────────────────────────────────

        inventory.setItem(SLOT_COMPANIONS, item(Material.EGG,
            "<gold><bold>Begleiter",
            "<gray>Beschwöre und verwalte",
            "<gray>deine treuen Begleiter.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_RUNES, item(Material.PURPLE_DYE,
            "<light_purple><bold>Runen",
            "<gray>Graviere Runen in deine",
            "<gray>Werkzeuge und Rüstung.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_DUNGEONS, item(Material.IRON_SWORD,
            "<red><bold>Dungeons",
            "<gray>Betritt instanzierte",
            "<gray>Dungeons und besiege Bosse.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_BATTLEPASS, item(Material.BLAZE_POWDER,
            "<gold><bold>Battle Pass",
            "<gray>Saisonales Progression-",
            "<gray>System mit Belohnungen.",
            "",
            "<yellow>» Klicken zum Öffnen"));

        inventory.setItem(SLOT_WEATHER, item(Material.LIGHTNING_ROD,
            "<yellow><bold>Wetter",
            "<gray>Aktuelles und nächstes",
            "<gray>Insel-Wetter.",
            "",
            "<yellow>» Klicken für Info"));

        inventory.setItem(SLOT_STARS, item(Material.GLOWSTONE_DUST,
            "<yellow><bold>Sternschnuppen",
            "<gray>Sternschnuppen fallen jede",
            "<gray>Nacht auf Inseln.",
            "",
            "<yellow>» Klicken für Info"));

        inventory.setItem(SLOT_CLOSE, closeButton());
        fillEmpty();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getRawSlot()) {

            // ── Reihe 1 ───────────────────────────────────────────────────────
            case SLOT_SPAWN -> {
                player.closeInventory();
                teleportToSpawn();
            }
            case SLOT_ISLAND -> {
                player.closeInventory();
                BentoBoxHook.teleportHome(player);
            }
            case SLOT_SHOP   -> new ShopGui(plugin, player).open(player);
            case SLOT_QUESTS -> {
                plugin.getQuestManager().loadPlayer(player.getUniqueId());
                new QuestGui(plugin, player).open(player);
            }
            case SLOT_ACHIEVEMENTS -> new AchievementGui(plugin, player).open(player);
            case SLOT_TITLES       -> new TitleGui(plugin, player).open(player);
            case SLOT_GENERATORS   -> {
                player.closeInventory();
                player.performCommand("phsk");
            }

            // ── Reihe 2 ───────────────────────────────────────────────────────
            case SLOT_DNA       -> plugin.getIslandDnaManager().showDna(player);
            case SLOT_RITUALS   -> plugin.getRitualManager().showRituals(player);
            case SLOT_CONTRACTS -> plugin.getContractManager().showBoard(player);
            case SLOT_STORY     -> plugin.getStoryManager().showStoryStatus(player);
            case SLOT_BLUEPRINTS -> {
                player.closeInventory();
                plugin.getBlueprintManager().listBlueprints(player);
            }
            case SLOT_VOIDFISHING -> {
                player.closeInventory();
                player.sendMessage(MM.deserialize(
                    "<dark_gray>[<blue><bold>Void-Fishing</bold></blue><dark_gray>] "
                    + "<gray>Angel in der Leere unter deiner Insel (Y < 0) mit einer normalen Angel. "
                    + "Je tiefer, desto seltener der Fang!"));
            }
            case SLOT_CHRONICLE -> {
                player.closeInventory();
                plugin.getChronicleManager().giveChronicle(player);
            }

            // ── Reihe 3 ───────────────────────────────────────────────────────
            case SLOT_COMPANIONS -> {
                player.closeInventory();
                player.performCommand("companion list");
            }
            case SLOT_RUNES -> {
                player.closeInventory();
                player.performCommand("rune list");
            }
            case SLOT_DUNGEONS -> {
                player.closeInventory();
                player.performCommand("dungeon list");
            }
            case SLOT_BATTLEPASS -> {
                player.closeInventory();
                player.performCommand("bp");
            }
            case SLOT_WEATHER -> {
                player.closeInventory();
                var w = plugin.getWeatherManager();
                player.sendMessage(MM.deserialize(
                    "<dark_gray>[<yellow><bold>Wetter</bold></yellow><dark_gray>] "
                    + "<gray>Aktuell: <white>" + w.getCurrent().displayName));
                player.sendMessage(MM.deserialize(
                    "<dark_gray>[<yellow><bold>Wetter</bold></yellow><dark_gray>] "
                    + "<gray>Als nächstes: <white>" + w.getNext().displayName));
                if (!w.getCurrent().effect.isEmpty()) {
                    player.sendMessage(MM.deserialize(
                        "<dark_gray>[<yellow><bold>Wetter</bold></yellow><dark_gray>] "
                        + "<gray>Effekt: <white>" + w.getCurrent().effect));
                }
            }
            case SLOT_STARS -> {
                player.closeInventory();
                player.sendMessage(MM.deserialize(
                    "<dark_gray>[<yellow><bold>Sterne</bold></yellow><dark_gray>] "
                    + "<gray>Jede Nacht fallen 1-3 Sternschnuppen auf zufällige Inseln. "
                    + "Sammle sie für Coins, Runen und seltene Items!"));
            }
            case SLOT_CLOSE -> player.closeInventory();
        }
    }

    private void teleportToSpawn() {
        ConfigurationSection spawnCfg = plugin.getConfig().getConfigurationSection("spawn");
        if (spawnCfg == null) return;
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(spawnCfg.getString("world", "world"));
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
}
