package de.pinkhorizon.skyblock.enums;

/** Rang basierend auf dem Insel-Level – wird im Chat angezeigt. */
public enum IslandRank {

    NEULING   (0,    "§8[Neuling]",          "§8"),
    ANFAENGER (1,    "§7[✦ Anfänger]",       "§7"),
    BRONZE    (10,   "§6[✦✦ Bronze]",        "§6"),
    SILBER    (50,   "§7[✦✦✦ Silber]",       "§7"),
    GOLD      (100,  "§e[✦✦✦✦ Gold]",        "§e"),
    DIAMANT   (250,  "§b[✦✦✦✦✦ Diamant]",   "§b"),
    KRISTALL  (500,  "§d[✦✦✦✦✦✦ Kristall]", "§d"),
    LEGENDE   (1000, "§c[✦✦✦✦✦✦✦ Legende]", "§c");

    private final long minLevel;
    private final String badge;
    private final String nameColor;

    IslandRank(long minLevel, String badge, String nameColor) {
        this.minLevel = minLevel;
        this.badge = badge;
        this.nameColor = nameColor;
    }

    public String getBadge()     { return badge; }
    public String getNameColor() { return nameColor; }

    public static IslandRank of(long islandLevel) {
        IslandRank best = NEULING;
        for (IslandRank r : values()) {
            if (islandLevel >= r.minLevel) best = r;
        }
        return best;
    }
}
