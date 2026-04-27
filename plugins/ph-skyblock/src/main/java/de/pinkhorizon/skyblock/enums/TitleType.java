package de.pinkhorizon.skyblock.enums;

public enum TitleType {

    // Standard (immer verfügbar)
    KEIN_TITEL      ("none",           "",                          "§8Kein Titel",              0),

    // Automatisch bei Insel-Erstellung vergeben
    INSELBESITZER   ("inselbesitzer",  "§a[Inselbesitzer]",         "§aInselbesitzer",           0),

    // Anfänger
    ANFAENGER       ("anfaenger",      "§7[Anfänger]",              "§7Anfänger",                0),
    SCHUERFER       ("schuerfer",      "§7[Schürfer]",              "§7Schürfer",                0),
    STEINBRECHER    ("steinbrecher",   "§7[Steinbrecher]",          "§7Steinbrecher",            0),

    // Fortgeschrittene
    ERZSUCHER       ("erzsucher",      "§6[Erzsucher]",             "§6Erzsucher",               0),
    PROFI_SCHUERFER ("profi_schuerfer","§6[Profi-Schürfer]",        "§6Profi-Schürfer",          0),
    DIAMANTJAEGER   ("diamantjaeger",  "§b[Diamantjäger]",          "§bDiamantjäger",            0),
    DER_REICHE      ("der_reiche",     "§e[Der Reiche]",            "§eDer Reiche",              0),
    GASTGEBER       ("gastgeber",      "§a[Gastgeber]",             "§aGastgeber",               0),
    TEAM_PLAYER     ("team_player",    "§a[Team-Player]",           "§aTeam-Player",             0),
    QUESTJAEGER     ("questjaeger",    "§a[Quest-Jäger]",           "§aQuest-Jäger",             0),
    EXPLORER        ("explorer",       "§e[Explorer]",              "§eExplorer",                0),

    // Experten
    NETHERIT_MEISTER("netherit_meister","§5[Netherit-Meister]",     "§5Netherit-Meister",        0),
    MILLIONAER      ("millionaer",     "§6[Millionär ✦]",           "§6Millionär",               0),
    INSELFUERST     ("inselfuerst",    "§d[Inselfürst]",            "§dInselfürst",              0),
    QUEST_MEISTER   ("quest_meister",  "§6[Quest-Meister]",         "§6Quest-Meister",           0),
    TREUER_SPIELER  ("treuer_spieler", "§a[Treuer Spieler ♥]",      "§aTreuer Spieler",          0),

    // Elite / Kaufbar
    INSEL_LEGENDE   ("insel_legende",  "§c[Insel-Legende ★]",       "§cInsel-Legende",           0),
    LEGENDE         ("legende",        "§6[§cLegende§6]",           "§cLegende",                 0),

    // Spezial (nur durch Achievements oder Events)
    GOTT_DER_INSELN ("gott_der_inseln","§d[✦ §cGott der Inseln §d✦]","§cGott der Inseln",        0),

    // Kaufbare Titel (aus dem NPC-Shop mit Coins)
    DER_BERGMANN    ("bergmann",       "§8[Bergmann ⛏]",            "§8Bergmann",                5000),
    DER_HAENDLER    ("haendler",       "§e[Händler ✦]",             "§eHändler",                 10000),
    DER_MAGIER      ("magier",         "§9[Magier ✦]",              "§9Magier",                  15000),
    DER_KRIEGER     ("krieger",        "§c[Krieger ⚔]",             "§cKrieger",                 20000),
    DER_ARCHITEKT   ("architekt",      "§a[Architekt ✦]",           "§aArchitekt",               25000),
    PINK_STAR       ("pink_star",      "§d[✦ Pink Star ✦]",         "§dPink Star",               50000),
    COSMIC_KING     ("cosmic_king",    "§5[✦✦ Cosmic King ✦✦]",     "§5Cosmic King",             100000);

    private final String id;
    private final String chatPrefix;   // Angezeigt im Chat vor dem Namen
    private final String displayName;  // Angezeigt in GUIs
    private final long buyPrice;       // 0 = nicht kaufbar (nur durch Achievement)

    TitleType(String id, String chatPrefix, String displayName, long buyPrice) {
        this.id = id;
        this.chatPrefix = chatPrefix;
        this.displayName = displayName;
        this.buyPrice = buyPrice;
    }

    private static final java.util.regex.Pattern LEGACY = java.util.regex.Pattern.compile("§[0-9a-fA-Fk-oK-OrR]");

    public String getId()          { return id; }
    public String getChatPrefix()  { return chatPrefix.isEmpty() ? "" : chatPrefix + " "; }
    public String getDisplayName() { return displayName; }
    /** Gibt den Anzeigenamen ohne Legacy-Farbcodes zurück – für MiniMessage-Kontexte. */
    public String getCleanDisplayName() { return LEGACY.matcher(displayName).replaceAll(""); }
    /** Gibt den Chat-Präfix ohne Legacy-Farbcodes zurück – für MiniMessage-Kontexte. */
    public String getCleanChatPrefix()  { return LEGACY.matcher(chatPrefix).replaceAll("").trim(); }
    public long getBuyPrice()      { return buyPrice; }
    public boolean isBuyable()     { return buyPrice > 0; }

    public static TitleType byId(String id) {
        if (id == null) return KEIN_TITEL;
        for (TitleType t : values()) if (t.id.equals(id)) return t;
        return KEIN_TITEL;
    }
}
