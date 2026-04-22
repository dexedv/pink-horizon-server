package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Vergibt passive Job-Boni (Tränkeffekte) alle 5 Minuten an Spieler mit aktivem Job.
 * Effekte laufen 7 Minuten → kein Lücken zwischen Anwendungen.
 *
 * Meilensteine je Job: Level 10 / 25 / 50 / 75 / 100
 */
public class JobBonusManager {

    /** 7 Minuten Effektdauer – überlappt das 5-min-Intervall um 2 min */
    private static final int DURATION = 7 * 60 * 20;

    private final PHSurvival plugin;
    private BukkitTask task;

    public JobBonusManager(PHSurvival plugin) {
        this.plugin = plugin;
        // Erste Anwendung nach 10 s, danach alle 5 min
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 200L, 6000L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    /** Sofortige Anwendung (z. B. beim Einloggen) */
    public void applyToPlayer(Player player) {
        apply(player);
    }

    // ── intern ───────────────────────────────────────────────────────────

    private void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) apply(p);
    }

    private void apply(Player player) {
        JobManager jm  = plugin.getJobManager();
        JobManager.Job job = jm.getJob(player.getUniqueId());
        if (job == null) return;
        int level = jm.getLevelForJob(player.getUniqueId(), job);
        for (PotionEffect e : bonuses(job, level)) {
            player.addPotionEffect(e);
        }
    }

    private List<PotionEffect> bonuses(JobManager.Job job, int level) {
        List<PotionEffect> list = new ArrayList<>();
        switch (job) {

            case MINER -> {
                // Lv10  Haste I
                // Lv25  Haste II
                // Lv50  + Nachtblick
                // Lv75  Haste III
                // Lv100 + Tempo I
                if (level >= 75)       add(list, PotionEffectType.HASTE, 2);
                else if (level >= 25)  add(list, PotionEffectType.HASTE, 1);
                else if (level >= 10)  add(list, PotionEffectType.HASTE, 0);
                if (level >= 50)  add(list, PotionEffectType.NIGHT_VISION, 0);
                if (level >= 100) add(list, PotionEffectType.SPEED, 0);
            }

            case LUMBERJACK -> {
                // Lv10  Tempo I
                // Lv25  + Haste I
                // Lv50  + Sprungkraft I
                // Lv75  Tempo II
                // Lv100 Haste II
                if (level >= 75)       add(list, PotionEffectType.SPEED, 1);
                else if (level >= 10)  add(list, PotionEffectType.SPEED, 0);
                if (level >= 25)  add(list, PotionEffectType.HASTE, level >= 100 ? 1 : 0);
                if (level >= 50)  add(list, PotionEffectType.JUMP_BOOST, 0);
            }

            case FARMER -> {
                // Lv10  Regeneration I
                // Lv25  + Tempo I
                // Lv50  + Glück I
                // Lv75  Regeneration II
                // Lv100 Glück II
                if (level >= 75)       add(list, PotionEffectType.REGENERATION, 1);
                else if (level >= 10)  add(list, PotionEffectType.REGENERATION, 0);
                if (level >= 25)  add(list, PotionEffectType.SPEED, 0);
                if (level >= 50)  add(list, PotionEffectType.LUCK, level >= 100 ? 1 : 0);
            }

            case HUNTER -> {
                // Lv10  Stärke I
                // Lv25  + Tempo I
                // Lv50  + Resistenz I
                // Lv75  Stärke II
                // Lv100 Resistenz II
                if (level >= 75)       add(list, PotionEffectType.STRENGTH, 1);
                else if (level >= 10)  add(list, PotionEffectType.STRENGTH, 0);
                if (level >= 25)  add(list, PotionEffectType.SPEED, 0);
                if (level >= 50)  add(list, PotionEffectType.RESISTANCE, level >= 100 ? 1 : 0);
            }

            case FISHER -> {
                // Lv10  Glück I
                // Lv25  + Wasseratmung
                // Lv50  + Delfinsgnade
                // Lv75  Glück II
                // Lv100 + Tempo I
                if (level >= 75)       add(list, PotionEffectType.LUCK, 1);
                else if (level >= 10)  add(list, PotionEffectType.LUCK, 0);
                if (level >= 25)  add(list, PotionEffectType.WATER_BREATHING, 0);
                if (level >= 50)  add(list, PotionEffectType.DOLPHINS_GRACE, 0);
                if (level >= 100) add(list, PotionEffectType.SPEED, 0);
            }

            case BREWER -> {
                // Lv10  Regeneration I
                // Lv25  + Resistenz I
                // Lv50  + Absorption I
                // Lv75  Regeneration II
                // Lv100 Absorption II
                if (level >= 75)       add(list, PotionEffectType.REGENERATION, 1);
                else if (level >= 10)  add(list, PotionEffectType.REGENERATION, 0);
                if (level >= 25)  add(list, PotionEffectType.RESISTANCE, 0);
                if (level >= 50)  add(list, PotionEffectType.ABSORPTION, level >= 100 ? 1 : 0);
            }

            case BUILDER -> {
                // Lv10  Haste I
                // Lv25  + Sprungkraft I
                // Lv50  + Tempo I
                // Lv75  Haste II
                // Lv100 Sprungkraft II
                if (level >= 75)       add(list, PotionEffectType.HASTE, 1);
                else if (level >= 10)  add(list, PotionEffectType.HASTE, 0);
                if (level >= 25)  add(list, PotionEffectType.JUMP_BOOST, level >= 100 ? 1 : 0);
                if (level >= 50)  add(list, PotionEffectType.SPEED, 0);
            }

            case ENCHANTER -> {
                // Lv10  Glück I
                // Lv25  + Nachtblick
                // Lv50  + Resistenz I
                // Lv75  Glück II
                // Lv100 + Absorption I
                if (level >= 75)       add(list, PotionEffectType.LUCK, 1);
                else if (level >= 10)  add(list, PotionEffectType.LUCK, 0);
                if (level >= 25)  add(list, PotionEffectType.NIGHT_VISION, 0);
                if (level >= 50)  add(list, PotionEffectType.RESISTANCE, 0);
                if (level >= 100) add(list, PotionEffectType.ABSORPTION, 0);
            }

            case WEAPONSMITH -> {
                // Lv10  Stärke I
                // Lv25  + Resistenz I
                // Lv50  + Feuerresistenz
                // Lv75  Stärke II
                // Lv100 + Haste I
                if (level >= 75)       add(list, PotionEffectType.STRENGTH, 1);
                else if (level >= 10)  add(list, PotionEffectType.STRENGTH, 0);
                if (level >= 25)  add(list, PotionEffectType.RESISTANCE, 0);
                if (level >= 50)  add(list, PotionEffectType.FIRE_RESISTANCE, 0);
                if (level >= 100) add(list, PotionEffectType.HASTE, 0);
            }
        }
        return list;
    }

    /**
     * ambient=true (wenige Partikel), particles=false (keine Partikel),
     * icon=true (sichtbar in der HUD-Leiste)
     */
    private void add(List<PotionEffect> list, PotionEffectType type, int amplifier) {
        list.add(new PotionEffect(type, DURATION, amplifier, true, false, true));
    }
}
