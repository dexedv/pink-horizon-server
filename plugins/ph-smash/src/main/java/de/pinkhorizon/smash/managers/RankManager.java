package de.pinkhorizon.smash.managers;

public class RankManager {

    private RankManager() {}

    /**
     * Returns the full display name including color for the given boss level.
     */
    public static String getRankDisplay(int level) {
        if (level >= 500) return "§4§lGötterkrieger";
        if (level >= 251) return "§dMythisch";
        if (level >= 101) return "§5Legende";
        if (level >= 51)  return "§bElite";
        if (level >= 26)  return "§6Krieger";
        if (level >= 11)  return "§aKämpfer";
        return "§7Neuling";
    }

    /**
     * Returns the short prefix for the tab list for the given boss level.
     */
    public static String getRankPrefix(int level) {
        if (level >= 500) return "§4[G]";
        if (level >= 251) return "§d[M]";
        if (level >= 101) return "§5[L]";
        if (level >= 51)  return "§b[E]";
        if (level >= 26)  return "§6[KR]";
        if (level >= 11)  return "§a[K]";
        return "§7[N]";
    }
}
