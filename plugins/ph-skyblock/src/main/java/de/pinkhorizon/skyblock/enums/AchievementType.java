package de.pinkhorizon.skyblock.enums;

import org.bukkit.Material;

public enum AchievementType {

    // ── Generator-Achievements ────────────────────────────────────────────────
    GEN_LVL_5  ("gen_5",     "§7Erster Schritt",         "Erreiche Generator Level §e5§7.",          Material.FURNACE,       50,   TitleType.STEINBRECHER),
    GEN_LVL_10 ("gen_10",    "§7Steinbrecher",           "Erreiche Generator Level §e10§7.",         Material.SMOKER,        150,  TitleType.STEINBRECHER),
    GEN_LVL_20 ("gen_20",    "§6Erzsucher",              "Erreiche Generator Level §e20§7.",         Material.IRON_BLOCK,    500,  TitleType.ERZSUCHER),
    GEN_LVL_35 ("gen_35",    "§bDiamantjäger",           "Erreiche Generator Level §e35§7.",         Material.DIAMOND_BLOCK, 2000, TitleType.DIAMANTJAEGER),
    GEN_LVL_50 ("gen_50",    "§5Netherit-Meister",       "Erreiche Generator Level §e50§7.",         Material.NETHERITE_BLOCK, 10000, TitleType.NETHERIT_MEISTER),

    // ── Mining-Achievements ───────────────────────────────────────────────────
    MINE_100   ("mine_100",  "§7Hobbyschürfer",          "Baue §e100§7 Blöcke ab.",                  Material.STONE,         25,   null),
    MINE_1K    ("mine_1k",   "§7Schürfer",               "Baue §e1.000§7 Blöcke ab.",                Material.COBBLESTONE,   100,  TitleType.SCHUERFER),
    MINE_10K   ("mine_10k",  "§6Erfahrener Schürfer",    "Baue §e10.000§7 Blöcke ab.",               Material.IRON_ORE,      500,  null),
    MINE_100K  ("mine_100k", "§bProfi-Schürfer",         "Baue §e100.000§7 Blöcke ab.",              Material.DIAMOND_ORE,   2500, TitleType.PROFI_SCHUERFER),
    MINE_1M    ("mine_1m",   "§dLegendes Schürfer",      "Baue §e1.000.000§7 Blöcke ab.",            Material.EMERALD_ORE,   15000, TitleType.LEGENDE),

    // ── Coin-Achievements ─────────────────────────────────────────────────────
    COINS_1K   ("coins_1k",  "§7Taschengeld",            "Verdiene §e1.000§7 Coins.",                Material.GOLD_NUGGET,   50,   null),
    COINS_10K  ("coins_10k", "§6Kleines Vermögen",       "Verdiene §e10.000§7 Coins.",               Material.GOLD_INGOT,    200,  null),
    COINS_100K ("coins_100k","§6Großes Vermögen",        "Verdiene §e100.000§7 Coins.",              Material.GOLD_BLOCK,    1000, TitleType.DER_REICHE),
    COINS_1M   ("coins_1m",  "§eMilionär",               "Verdiene §e1.000.000§7 Coins.",            Material.NETHER_STAR,   5000, TitleType.MILLIONAER),

    // ── Insel-Score-Achievements ──────────────────────────────────────────────
    SCORE_100  ("score_100", "§7Insel-Anfänger",         "Erreiche einen Insel-Score von §e100§7.",  Material.GRASS_BLOCK,   100,  null),
    SCORE_1K   ("score_1k",  "§aInsel-Aufsteiger",       "Erreiche einen Insel-Score von §e1.000§7.",Material.OAK_LOG,       500,  null),
    SCORE_10K  ("score_10k", "§bInsel-Profi",            "Erreiche einen Insel-Score von §e10.000§7.",Material.DIAMOND_BLOCK,2500, TitleType.INSELFUERST),
    SCORE_100K ("score_100k","§dInsel-Legende",          "Erreiche einen Insel-Score von §e100.000§7.",Material.BEACON,     15000, TitleType.INSEL_LEGENDE),

    // ── Sozial-Achievements ───────────────────────────────────────────────────
    INVITE_1   ("invite_1",  "§7Gastgeber",              "Lade §e1§7 Spieler auf deine Insel ein.",   Material.OAK_DOOR,     100,  null),
    INVITE_5   ("invite_5",  "§aGeselliger",             "Lade §e5§7 Spieler auf deine Insel ein.",   Material.IRON_DOOR,    500,  TitleType.GASTGEBER),
    INVITE_10  ("invite_10", "§6Team-Player",            "Lade §e10§7 Spieler auf deine Insel ein.",  Material.DIAMOND_SWORD,2000, TitleType.TEAM_PLAYER),
    VISIT_5    ("visit_5",   "§7Weltenbummler",          "Besuche §e5§7 verschiedene Inseln.",         Material.COMPASS,      100,  null),
    VISIT_25   ("visit_25",  "§eExpeditionsforscher",    "Besuche §e25§7 verschiedene Inseln.",        Material.MAP,          500,  TitleType.EXPLORER),
    VISIT_100  ("visit_100", "§bGrand-Explorer",         "Besuche §e100§7 verschiedene Inseln.",       Material.FILLED_MAP,   2500, null),

    // ── Quest-Achievements ────────────────────────────────────────────────────
    QUEST_10   ("quest_10",  "§7Quest-Einsteiger",       "Schließe §e10§7 tägliche Quests ab.",       Material.PAPER,        200,  null),
    QUEST_50   ("quest_50",  "§aQuest-Veterane",         "Schließe §e50§7 tägliche Quests ab.",       Material.BOOK,         1000, TitleType.QUESTJAEGER),
    QUEST_200  ("quest_200", "§6Quest-Meister",          "Schließe §e200§7 tägliche Quests ab.",      Material.ENCHANTED_BOOK,5000, TitleType.QUEST_MEISTER),

    // ── Erste Schritte ────────────────────────────────────────────────────────
    FIRST_ISLAND("first_island", "§aDer Anfang",         "Erstelle deine erste Insel.",               Material.OAK_SAPLING,  50,   TitleType.ANFAENGER),
    FIRST_GEN   ("first_gen",   "§7Erster Generator",   "Platziere deinen ersten Generator.",         Material.FURNACE,      50,   null),
    FIRST_SELL  ("first_sell",  "§6Erstes Geschäft",    "Nutze Auto-Sell zum ersten Mal.",             Material.EMERALD,      50,   null),
    LOGIN_STREAK_7("streak_7",  "§aEine Woche dabei",   "Logge dich §e7§7 Tage in Folge ein.",         Material.SUNFLOWER,    500,  null),
    LOGIN_STREAK_30("streak_30","§6Treuer Spieler",     "Logge dich §e30§7 Tage in Folge ein.",        Material.GOLDEN_APPLE, 2500, TitleType.TREUER_SPIELER),

    // ── Epische Achievements ──────────────────────────────────────────────────
    COMPLETE_ALL("complete_all","§6§lAlles gemeistert!","Schließe §c100§6 Achievements ab.",           Material.NETHER_STAR,  50000, TitleType.GOTT_DER_INSELN);

    private final String id;
    private final String name;
    private final String description;
    private final Material icon;
    private final long coinReward;
    private final TitleType titleReward; // kann null sein

    AchievementType(String id, String name, String description, Material icon,
                    long coinReward, TitleType titleReward) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.coinReward = coinReward;
        this.titleReward = titleReward;
    }

    public String getId()            { return id; }
    public String getName()          { return name; }
    public String getDescription()   { return description; }
    public Material getIcon()        { return icon; }
    public long getCoinReward()      { return coinReward; }
    public TitleType getTitleReward(){ return titleReward; }

    public static AchievementType byId(String id) {
        for (AchievementType a : values()) if (a.id.equals(id)) return a;
        return null;
    }
}
