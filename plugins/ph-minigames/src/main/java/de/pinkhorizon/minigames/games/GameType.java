package de.pinkhorizon.minigames.games;

public enum GameType {
    BEDWARS("BedWars"),
    SKYWARS("SkyWars");

    private final String displayName;

    GameType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
