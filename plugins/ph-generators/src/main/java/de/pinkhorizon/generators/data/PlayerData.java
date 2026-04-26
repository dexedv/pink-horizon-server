package de.pinkhorizon.generators.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    // Täglicher Bonus
    private long lastDaily = 0;       // Unix-Timestamp des letzten Daily-Claims
    private int dailyStreak = 0;      // Aktuelle Streak-Länge

    // Auto-Upgrade
    private boolean autoUpgrade = false;

    // Upgrade-Tokens (kostenlose Upgrades)
    private int upgradeTokens = 0;

    // Permanente Bonus-Slots (Tebex-Käufe)
    private int bonusSlots = 0;

    // Prestige-Tokens (überspringen die Geld-Anforderung)
    private int prestigeTokens = 0;

    // Talent-System
    private int talentPoints = 0;
    private final Set<String> unlockedTalents = new HashSet<>();

    // Höchste erreichte Milestone-Stufe
    private int milestoneReached = 0;

    // Session-only: Rückkehr-Position für /gen visit
    private transient String returnWorld = null;
    private transient double returnX, returnY, returnZ;

    // Generatoren (in-memory, aus gen_generators geladen)
    private final List<PlacedGenerator> generators = new ArrayList<>();

    // Aktiver Booster (Ablauf-Timestamp in Sekunden, 0 = kein Booster)
    private long boosterExpiry = 0;
    private double boosterMultiplier = 1.0;

    // Gespeicherte (noch nicht aktivierte) Booster (Tebex-Käufe)
    private final List<StoredBooster> storedBoosters = new ArrayList<>();

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

    // Rang aus pinkhorizon.players (session-only, wird beim Join geladen)
    private transient String rank = "spieler";

    // LP-Gruppe (session-only, wird beim Join über LuckPerms gesetzt)
    private transient String lpGroup = "default";

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

    // ── Stored Boosters (Tebex-Käufe) ───────────────────────────────────────

    public List<StoredBooster> getStoredBoosters() { return storedBoosters; }

    public void addStoredBooster(StoredBooster b) { storedBoosters.add(b); }

    public boolean removeStoredBooster(int index) {
        if (index < 0 || index >= storedBoosters.size()) return false;
        storedBoosters.remove(index);
        return true;
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
        return maxGeneratorSlots(baseSlots, prestigePerSlot, 0);
    }

    public int maxGeneratorSlots(int baseSlots, int prestigePerSlot, int talentSlots) {
        return baseSlots + (prestige / prestigePerSlot) + getRankExtraSlots() + talentSlots + bonusSlots;
    }

    // ── Rang-Perks (LP-Gruppe) ───────────────────────────────────────────────

    public String getLpGroup()              { return lpGroup; }
    public void   setLpGroup(String g)      { this.lpGroup = g == null ? "default" : g; }

    /** Einkommens-Multiplikator durch gekauften Rang. */
    public double getRankMultiplier() {
        return switch (lpGroup) {
            case "nexus"    -> 1.15;
            case "catalyst" -> 1.10;
            case "rune"     -> 1.05;
            default         -> 1.0;
        };
    }

    /** Extra Generator-Slots durch gekauften Rang. */
    public int getRankExtraSlots() {
        return switch (lpGroup) {
            case "nexus"    -> 6;
            case "catalyst" -> 4;
            case "rune"     -> 2;
            default         -> 0;
        };
    }

    /** true wenn der Rang Auto-Upgrade erlaubt (Catalyst / Nexus). */
    public boolean rankAllowsAutoUpgrade() {
        return lpGroup.equals("catalyst") || lpGroup.equals("nexus");
    }

    /** true wenn der Rang Bulk-Upgrade erlaubt (Rune / Catalyst / Nexus). */
    public boolean rankAllowsBulkUpgrade() {
        return lpGroup.equals("rune") || lpGroup.equals("catalyst") || lpGroup.equals("nexus");
    }

    /** true wenn der Rang Auto-Tier-Upgrade erlaubt (nur Nexus). */
    public boolean rankAllowsTierUpgradeAll() {
        return lpGroup.equals("nexus");
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

    // ── Daily Bonus ──────────────────────────────────────────────────────────
    public long getLastDaily()                     { return lastDaily; }
    public void setLastDaily(long ts)              { this.lastDaily = ts; }
    public int getDailyStreak()                    { return dailyStreak; }
    public void setDailyStreak(int s)              { this.dailyStreak = s; }

    // ── Auto-Upgrade ─────────────────────────────────────────────────────────
    public boolean isAutoUpgrade()                 { return autoUpgrade; }
    public void setAutoUpgrade(boolean b)          { this.autoUpgrade = b; }

    // ── Upgrade-Tokens ───────────────────────────────────────────────────────
    public int getUpgradeTokens()                  { return upgradeTokens; }
    public void setUpgradeTokens(int n)            { this.upgradeTokens = n; }
    public void addUpgradeTokens(int n)            { this.upgradeTokens += n; }
    public boolean useUpgradeToken()               {
        if (upgradeTokens <= 0) return false;
        upgradeTokens--;
        return true;
    }

    // ── Bonus-Slots (Tebex) ──────────────────────────────────────────────────
    public int getBonusSlots()                      { return bonusSlots; }
    public void setBonusSlots(int n)                { this.bonusSlots = n; }
    public void addBonusSlots(int n)                { this.bonusSlots += n; }

    // ── Prestige-Tokens (Tebex) ──────────────────────────────────────────────
    public int getPrestigeTokens()                  { return prestigeTokens; }
    public void setPrestigeTokens(int n)            { this.prestigeTokens = n; }
    public void addPrestigeTokens(int n)            { this.prestigeTokens += n; }
    public boolean usePrestigeToken()               {
        if (prestigeTokens <= 0) return false;
        prestigeTokens--;
        return true;
    }

    // ── Talent-System ────────────────────────────────────────────────────────
    public int getTalentPoints()                   { return talentPoints; }
    public void setTalentPoints(int n)             { this.talentPoints = n; }
    public void addTalentPoints(int n)             { this.talentPoints += n; }
    public Set<String> getUnlockedTalents()        { return unlockedTalents; }
    public boolean hasTalent(String id)            { return unlockedTalents.contains(id); }
    public void unlockTalent(String id)            { unlockedTalents.add(id); }

    // ── Milestones ───────────────────────────────────────────────────────────
    public int getMilestoneReached()               { return milestoneReached; }
    public void setMilestoneReached(int n)         { this.milestoneReached = n; }

    // ── Return Location (session-only) ───────────────────────────────────────
    public boolean hasReturnLocation()             { return returnWorld != null; }
    public String getReturnWorld()                 { return returnWorld; }
    public double getReturnX()                     { return returnX; }
    public double getReturnY()                     { return returnY; }
    public double getReturnZ()                     { return returnZ; }
    public void setReturnLocation(String world, double x, double y, double z) {
        this.returnWorld = world; this.returnX = x; this.returnY = y; this.returnZ = z;
    }
    public void clearReturnLocation()              { this.returnWorld = null; }

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
