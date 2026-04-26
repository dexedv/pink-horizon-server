package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

/**
 * Verwaltet serverweite Events (DOUBLE_INCOME, UPGRADE_SALE, LUCKY_HOUR).
 */
public class EventManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum EventType {
        DOUBLE_INCOME("x2 Einkommen", "<yellow>Alle Generatoren verdienen doppelt!"),
        UPGRADE_SALE("50% Upgrade-Rabatt", "<aqua>Alle Upgrades kosten nur die Hälfte!"),
        LUCKY_HOUR("Lucky Hour", "<light_purple>Doppelte Enchant-Chance bei Platzierungen!");

        final String name;
        final String description;

        EventType(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    private EventType activeEvent = null;
    private long eventExpiry = 0;

    // Community Goal: $1T server total earned → 2h server booster
    private long communityProgress = 0;
    private static final long COMMUNITY_GOAL = 1_000_000_000_000L;
    private boolean communityGoalReached = false;

    public EventManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void startEvent(EventType type, int durationMinutes) {
        this.activeEvent = type;
        this.eventExpiry = System.currentTimeMillis() / 1000 + (long) durationMinutes * 60;

        Bukkit.broadcast(MM.deserialize(
                "<gold><bold>✦ SERVER EVENT: " + type.name + " ✦</bold></gold>\n"
                + "<yellow>" + type.description + "\n"
                + "<gray>Dauer: " + durationMinutes + " Minuten"));
    }

    public void stopEvent() {
        if (activeEvent == null) return;
        Bukkit.broadcast(MM.deserialize("<gray>Das Server-Event <yellow>" + activeEvent.name + " <gray>ist beendet."));
        activeEvent = null;
        eventExpiry = 0;
    }

    public boolean isEventActive() {
        if (activeEvent == null) return false;
        if (System.currentTimeMillis() / 1000 > eventExpiry) {
            stopEvent();
            return false;
        }
        return true;
    }

    public EventType getActiveEvent() {
        return isEventActive() ? activeEvent : null;
    }

    /** Einkommens-Multiplikator durch aktives Event */
    public double getIncomeMultiplier() {
        if (!isEventActive()) return 1.0;
        return activeEvent == EventType.DOUBLE_INCOME ? 2.0 : 1.0;
    }

    /** Upgrade-Kosten-Multiplikator durch aktives Event */
    public double getUpgradeCostMultiplier() {
        if (!isEventActive()) return 1.0;
        return activeEvent == EventType.UPGRADE_SALE ? 0.5 : 1.0;
    }

    /** Erhöhte Enchant-Chance durch Lucky Hour */
    public double getEnchantChanceBonus() {
        if (!isEventActive()) return 0.0;
        return activeEvent == EventType.LUCKY_HOUR ? 0.05 : 0.0;
    }

    /** Community Goal: verfolgt serverweiten Fortschritt */
    public void addCommunityProgress(long amount) {
        if (communityGoalReached) return;
        communityProgress += amount;
        if (communityProgress >= COMMUNITY_GOAL) {
            communityGoalReached = true;
            triggerCommunityReward();
        }
    }

    private void triggerCommunityReward() {
        Bukkit.broadcast(MM.deserialize(
                "<gold><bold>✦✦ COMMUNITY ZIEL ERREICHT! ✦✦</bold></gold>\n"
                + "<yellow>Zusammen habt ihr $1T verdient!\n"
                + "<green>Belohnung: 2h x2 Server-Booster für alle!"));
        plugin.getMoneyManager().activateServerBooster(2.0, 120);
        // Reset für nächste Runde
        communityProgress = 0;
        communityGoalReached = false;
    }

    public long getCommunityProgress() { return communityProgress; }
    public long getCommunityGoal()     { return COMMUNITY_GOAL; }
    public long getEventExpiry()       { return eventExpiry; }

    public String getEventStatus() {
        if (!isEventActive()) return "<gray>Kein Event aktiv.";
        long rem = eventExpiry - System.currentTimeMillis() / 1000;
        return "<gold>Aktives Event: <yellow>" + activeEvent.name + " <gray>| noch " + rem / 60 + "m " + rem % 60 + "s";
    }
}
