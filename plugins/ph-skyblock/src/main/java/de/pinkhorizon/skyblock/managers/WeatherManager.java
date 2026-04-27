package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Dynamisches Wetter-System: Custom Wetter-Zyklen die Erträge beeinflussen.
 * Unabhängig vom Vanilla-Wettersystem.
 */
public class WeatherManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum WeatherType {
        SUNNY        ("☀ Sonnig",          "",                   0),
        GENTLE_RAIN  ("🌧 Sanfter Regen",  "<aqua>Crop-Wachstum +15%",   15),
        THUNDERSTORM ("⛈ Gewitter",        "<yellow>Crops +30%, Lightning-Drops möglich", 35),
        DROUGHT      ("🌵 Dürre",          "<red>Crops -30%, Mining-XP +20%", 30),
        VOID_MIST    ("🌫 Void-Nebel",     "<dark_aqua>Void-Fishing Rare-Chance +50%", 20),
        STARFALL     ("⭐ Sternennacht",    "<yellow>Sternschnuppen 2x häufiger", 999);

        public final String displayName;
        public final String effect;
        public final int    durationMinutes;

        WeatherType(String d, String e, int m) { displayName = d; effect = e; durationMinutes = m; }
    }

    // Gewichtungen für Random-Auswahl (STARFALL nicht random, nur 1x/Woche)
    private static final Map<WeatherType, Integer> WEIGHTS = Map.of(
        WeatherType.SUNNY,        40,
        WeatherType.GENTLE_RAIN,  25,
        WeatherType.THUNDERSTORM, 10,
        WeatherType.DROUGHT,      15,
        WeatherType.VOID_MIST,    10
    );

    private final PHSkyBlock plugin;
    private final Random rng = new Random();
    private WeatherType current = WeatherType.SUNNY;
    private WeatherType next    = WeatherType.SUNNY;

    // Aktive Boni (in %)
    private int cropBonus  = 0;
    private int miningBonus = 0;

    public WeatherManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        startWeatherCycle();
        scheduleStarfall();
    }

    private void startWeatherCycle() {
        rollNextWeather();
        new BukkitRunnable() {
            @Override public void run() {
                changeWeather();
            }
        }.runTaskTimer(plugin, 20L * 60 * 20, 20L * 60 * 20); // alle 20 Minuten prüfen
    }

    private void scheduleStarfall() {
        // Einmal pro Woche (in Ticks: 7 Tage × 24h × 60min × 60s × 20ticks)
        long weekTicks = 7L * 24 * 60 * 60 * 20;
        plugin.getServer().getScheduler().runTaskLater(plugin, this::triggerStarfall,
            weekTicks + rng.nextInt(20 * 60 * 60)); // random offset
    }

    private void changeWeather() {
        // Ankündigung 2 Minuten vorher
        announceUpcoming();
        // Wetter nach 2 Minuten wechseln
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            current = next;
            applyWeatherBonuses();
            announceActive();
            rollNextWeather();
        }, 20L * 60 * 2);
    }

    private void rollNextWeather() {
        int total = WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();
        int roll  = rng.nextInt(total);
        int cum   = 0;
        for (var entry : WEIGHTS.entrySet()) {
            cum += entry.getValue();
            if (roll < cum) { next = entry.getKey(); return; }
        }
        next = WeatherType.SUNNY;
    }

    private void applyWeatherBonuses() {
        cropBonus   = 0;
        miningBonus = 0;

        var worldOpt = BentoBoxHook.getSkyBlockWorld();
        worldOpt.ifPresent(w -> {
            switch (current) {
                case GENTLE_RAIN   -> { w.setStorm(true);  cropBonus = 15; }
                case THUNDERSTORM  -> { w.setThundering(true); cropBonus = 30; }
                case DROUGHT       -> { w.setStorm(false); cropBonus = -30; miningBonus = 20; }
                case VOID_MIST     -> { w.setStorm(false); /* handled in VoidFishingManager */ }
                case STARFALL      -> { w.setStorm(false); }
                default            -> { w.setStorm(false); }
            }
        });
    }

    private void announceUpcoming() {
        String msg = "<dark_gray>[Wetter] <gray>In 2 Minuten: " + next.displayName;
        if (!next.effect.isEmpty()) msg += " <dark_gray>│ " + next.effect;
        String finalMsg = msg;
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(MM.deserialize(finalMsg)));
    }

    private void announceActive() {
        String msg = "<dark_gray>[Wetter] " + current.displayName + "!";
        if (!current.effect.isEmpty()) msg += " <dark_gray>│ " + current.effect;
        String finalMsg = msg;
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(MM.deserialize(finalMsg)));
    }

    private void triggerStarfall() {
        current = WeatherType.STARFALL;
        applyWeatherBonuses();
        announceActive();
        // Zurück zu SUNNY nach 30min
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            current = WeatherType.SUNNY;
            applyWeatherBonuses();
        }, 20L * 60 * 30);
        scheduleStarfall(); // nächsten planen
    }

    // ── Abfragen ──────────────────────────────────────────────────────────────

    public WeatherType getCurrent() { return current; }
    public WeatherType getNext()    { return next; }
    public int getCropBonus()       { return cropBonus; }
    public int getMiningBonus()     { return miningBonus; }

    public boolean isVoidMist() {
        return current == WeatherType.VOID_MIST;
    }

    public boolean isStarfall() {
        return current == WeatherType.STARFALL;
    }
}
