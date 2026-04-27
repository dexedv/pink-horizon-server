package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Verwaltet serverweite Events (DOUBLE_INCOME, UPGRADE_SALE, LUCKY_HOUR).
 * Unterstützt automatisches Event-Scheduling via config.yml (auto-events).
 */
public class EventManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum EventType {
        DOUBLE_INCOME("x2 Einkommen",       "<yellow>Alle Generatoren verdienen doppelt!"),
        UPGRADE_SALE("50% Upgrade-Rabatt",  "<aqua>Alle Upgrades kosten nur die Hälfte!"),
        LUCKY_HOUR  ("Lucky Hour",          "<light_purple>Doppelte Enchant-Chance bei Platzierungen!");

        final String name;
        final String description;

        EventType(String name, String description) {
            this.name        = name;
            this.description = description;
        }
    }

    private EventType activeEvent = null;
    private long      eventExpiry = 0;

    // Community Goal: $1T server total earned → 2h server booster
    private long    communityProgress   = 0;
    private static final long COMMUNITY_GOAL = 1_000_000_000_000L;
    private boolean communityGoalReached = false;

    // Auto-Scheduler
    private BukkitTask autoTask      = null;
    private long       nextEventTime = 0;        // Unix-Sekunden
    private boolean    announcedNext = false;    // 5-Min-Ankündigung schon gesendet?

    public EventManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    // ── Lebenszyklus ────────────────────────────────────────────────────────────

    public void start() {
        if (!plugin.getConfig().getBoolean("auto-events.enabled", true)) return;

        int initialDelay = plugin.getConfig().getInt("auto-events.initial-delay-minutes", 15);
        nextEventTime = System.currentTimeMillis() / 1000 + (long) initialDelay * 60;
        announcedNext = false;

        autoTask = new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30); // alle 30 Sekunden
    }

    public void stop() {
        if (autoTask != null) { autoTask.cancel(); autoTask = null; }
    }

    // ── Interner Tick ───────────────────────────────────────────────────────────

    private void tick() {
        long now = System.currentTimeMillis() / 1000;

        // Laufendes Event beenden wenn Zeit abgelaufen
        if (activeEvent != null && now > eventExpiry) {
            broadcastEventEnd();
            activeEvent = null;
            eventExpiry = 0;
            scheduleNextEvent(now);
            return;
        }

        if (activeEvent != null) return; // Event läuft noch

        int announceMin = plugin.getConfig().getInt("auto-events.announce-before-minutes", 5);

        // Ankündigung X Minuten vorher (einmalig)
        if (!announcedNext && nextEventTime > now
                && (nextEventTime - now) <= (long) announceMin * 60) {
            announcedNext = true;
            EventType next = randomEventType();
            Bukkit.broadcast(MM.deserialize(
                    "<gold><bold>✦ EVENT IN " + announceMin + " MINUTEN! ✦</bold>"
                    + "\n<yellow>Bald startet: <white>" + next.name
                    + "\n" + next.description
                    + "\n<gray>Seid bereit!"));
        }

        // Event starten
        if (now >= nextEventTime) {
            int duration = plugin.getConfig().getInt("auto-events.duration-minutes", 30);
            startEvent(randomEventType(), duration);
        }
    }

    private void scheduleNextEvent(long now) {
        int cooldown = plugin.getConfig().getInt("auto-events.cooldown-minutes", 90);
        nextEventTime = now + (long) cooldown * 60;
        announcedNext = false;
    }

    private EventType randomEventType() {
        EventType[] types = EventType.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }

    // ── Öffentliche API ─────────────────────────────────────────────────────────

    public void startEvent(EventType type, int durationMinutes) {
        this.activeEvent = type;
        this.eventExpiry = System.currentTimeMillis() / 1000 + (long) durationMinutes * 60;

        Bukkit.broadcast(MM.deserialize(
                "<gold><bold>✦ SERVER EVENT: " + type.name + " ✦</bold>"
                + "\n" + type.description
                + "\n<gray>Dauer: <yellow>" + durationMinutes + " Minuten"));
    }

    public void stopEvent() {
        if (activeEvent == null) return;
        broadcastEventEnd();
        activeEvent = null;
        eventExpiry = 0;
        if (autoTask != null) scheduleNextEvent(System.currentTimeMillis() / 1000);
    }

    private void broadcastEventEnd() {
        Bukkit.broadcast(MM.deserialize(
                "<gray>Das Server-Event <yellow>" + activeEvent.name + " <gray>ist beendet."));
    }

    public boolean isEventActive() {
        if (activeEvent == null) return false;
        if (System.currentTimeMillis() / 1000 > eventExpiry) {
            broadcastEventEnd();
            activeEvent = null;
            eventExpiry = 0;
            if (autoTask != null) scheduleNextEvent(System.currentTimeMillis() / 1000);
            return false;
        }
        return true;
    }

    public EventType getActiveEvent() { return isEventActive() ? activeEvent : null; }

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

    // ── Community Goal ──────────────────────────────────────────────────────────

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
                "<gold><bold>✦✦ COMMUNITY ZIEL ERREICHT! ✦✦</bold>"
                + "\n<yellow>Zusammen habt ihr $1T verdient!"
                + "\n<green>Belohnung: 2h x2 Server-Booster für alle!"));
        plugin.getMoneyManager().activateServerBooster(2.0, 120);
        communityProgress   = 0;
        communityGoalReached = false;
    }

    // ── Getter ──────────────────────────────────────────────────────────────────

    public long getCommunityProgress() { return communityProgress; }
    public long getCommunityGoal()     { return COMMUNITY_GOAL; }
    public long getEventExpiry()       { return eventExpiry; }
    public long getNextEventTime()     { return nextEventTime; }

    public String getEventStatus() {
        long now = System.currentTimeMillis() / 1000;
        if (!isEventActive()) {
            if (nextEventTime > now) {
                long rem = nextEventTime - now;
                return "<gray>Kein Event aktiv. <yellow>Nächstes Event in <white>"
                        + rem / 60 + "m " + rem % 60 + "s";
            }
            return "<gray>Kein Event aktiv.";
        }
        long rem = eventExpiry - now;
        return "<gold>Aktives Event: <yellow>" + activeEvent.name
                + " <gray>| noch <white>" + rem / 60 + "m " + rem % 60 + "s";
    }
}
