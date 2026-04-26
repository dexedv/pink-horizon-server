package de.pinkhorizon.generators.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Alle Spieler-Daten für den Generator-Spielmodus.
 * Wird beim Join geladen und beim Logout / periodisch gespeichert.
 */
public class PlayerData {

    private final UUID uuid;
    private String name;
    private long money;
    private int prestige;
    private long lastSeen;  // Unix-Timestamp in Sekunden

    // Zähler für Achievements
    private long totalEarned;         // gesamtes Geld (ever earned)
    private int totalUpgrades;        // gesamt durchgeführte Upgrades
    private int afkBoxesOpened;       // geöffnete AFK-Boxen

    // Generatoren (in-memory, aus gen_generators geladen)
    private final List<PlacedGenerator> generators = new ArrayList<>();

    // Aktiver Booster (Ablauf-Timestamp in Sekunden, 0 = kein Booster)
    private long boosterExpiry = 0;
    private double boosterMultiplier = 1.0;

    // Insel-Border
    private int borderSize = 40;

    // Tutorial
    private boolean tutorialDone = false;

    // Stats-Hologramm (Spieler-Selbst-Platzierung)
    private String holoWorld = null;
    private int holoX, holoY, holoZ;

    // Ranglisten-Hologramm (Spieler-Selbst-Platzierung)
    private String lbHoloWorld = null;
    private int lbHoloX, lbHoloY, lbHoloZ;

    public PlayerData(UUID uuid, String name, long money, int prestige, long lastSeen) {
        this.uuid = uuid;
        this.name = name;
        this.money = money;
        this.prestige = prestige;
        this.lastSeen = lastSeen;
    }

    // ── Prestige-Logik ───────────────────────────────────────────────────────

    /** Maximales Generator-Level für diesen Spieler: prestige * 10, mind. 10 */
    public int maxGeneratorLevel() {
        return Math.max(10, prestige * 10);
    }

    /** Kosten für das nächste Prestige (prestige+1) * 100.000.000 */
    public long nextPrestigeCost() {
        return (long) (prestige + 1) * 100_000_000L;
    }

    /** Permanenter Einkommens-Multiplikator durch Prestige */
    public double prestigeMultiplier() {
        return 1.0 + (prestige * 0.05);
    }

    // ── Booster ──────────────────────────────────────────────────────────────

    public boolean hasActiveBooster() {
        return boosterExpiry > System.currentTimeMillis() / 1000;
    }

    public double effectiveBoosterMultiplier() {
        return hasActiveBooster() ? boosterMultiplier : 1.0;
    }

    public void activateBooster(double multiplier, int durationSeconds) {
        this.boosterMultiplier = multiplier;
        this.boosterExpiry = System.currentTimeMillis() / 1000 + durationSeconds;
    }

    // ── Geld ─────────────────────────────────────────────────────────────────

    public void addMoney(long amount) {
        this.money += amount;
        this.totalEarned += amount;
    }

    public boolean takeMoney(long amount) {
        if (this.money < amount) return false;
        this.money -= amount;
        return true;
    }

    // ── Generator-Slot-Limit ─────────────────────────────────────────────────

    /**
     * Max. Slots = Basis + 1 pro jeweils 2 Prestige-Stufen.
     * Basis aus config, Default 10.
     */
    public int maxGeneratorSlots(int baseSlots, int prestigePerSlot) {
        return baseSlots + (prestige / prestigePerSlot);
    }

    // ── Getter & Setter ──────────────────────────────────────────────────────

    public UUID getUuid()                          { return uuid; }
    public String getName()                        { return name; }
    public long getMoney()                         { return money; }
    public int getPrestige()                       { return prestige; }
    public long getLastSeen()                      { return lastSeen; }
    public long getTotalEarned()                   { return totalEarned; }
    public int getTotalUpgrades()                  { return totalUpgrades; }
    public int getAfkBoxesOpened()                 { return afkBoxesOpened; }
    public List<PlacedGenerator> getGenerators()   { return generators; }
    public long getBoosterExpiry()                 { return boosterExpiry; }
    public double getBoosterMultiplier()           { return boosterMultiplier; }

    public void setName(String name)               { this.name = name; }
    public void setMoney(long money)               { this.money = money; }
    public void setPrestige(int prestige)          { this.prestige = prestige; }
    public void setLastSeen(long lastSeen)         { this.lastSeen = lastSeen; }
    public void setTotalEarned(long totalEarned)   { this.totalEarned = totalEarned; }
    public void setTotalUpgrades(int n)            { this.totalUpgrades = n; }
    public void incrementUpgrades()                { this.totalUpgrades++; }
    public void setAfkBoxesOpened(int n)           { this.afkBoxesOpened = n; }
    public void incrementAfkBoxes()                { this.afkBoxesOpened++; }
    public void setBoosterExpiry(long ts)          { this.boosterExpiry = ts; }
    public void setBoosterMultiplier(double m)     { this.boosterMultiplier = m; }
    public int getBorderSize()                     { return borderSize; }
    public void setBorderSize(int borderSize)      { this.borderSize = Math.max(40, borderSize); }

    // ── Stats-Hologramm ──────────────────────────────────────────────────────

    public boolean hasStatsHolo()                  { return holoWorld != null; }
    public String getHoloWorld()                   { return holoWorld; }
    public int getHoloX()                          { return holoX; }
    public int getHoloY()                          { return holoY; }
    public int getHoloZ()                          { return holoZ; }

    public void setHoloLocation(String world, int x, int y, int z) {
        this.holoWorld = world; this.holoX = x; this.holoY = y; this.holoZ = z;
    }
    public void clearHoloLocation() { this.holoWorld = null; }

    // ── Ranglisten-Hologramm ─────────────────────────────────────────────────

    public boolean hasLbHolo()                   { return lbHoloWorld != null; }
    public String getLbHoloWorld()               { return lbHoloWorld; }
    public int getLbHoloX()                      { return lbHoloX; }
    public int getLbHoloY()                      { return lbHoloY; }
    public int getLbHoloZ()                      { return lbHoloZ; }

    public void setLbHoloLocation(String world, int x, int y, int z) {
        this.lbHoloWorld = world; this.lbHoloX = x; this.lbHoloY = y; this.lbHoloZ = z;
    }
    public void clearLbHoloLocation() { this.lbHoloWorld = null; }

    // ── Tutorial ─────────────────────────────────────────────────────────────

    public boolean isTutorialDone()              { return tutorialDone; }
    public void setTutorialDone(boolean done)    { this.tutorialDone = done; }
}
