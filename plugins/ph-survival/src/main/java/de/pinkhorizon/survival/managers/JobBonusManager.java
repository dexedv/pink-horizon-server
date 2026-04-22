package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Job-Passivboni – manuell ausgelöst via Shift+F (Schleichen + Tauschen-Taste).
 * Cooldown: 5 Minuten | Effektdauer: 6 Minuten
 *
 * Meilensteine: Level 10 / 25 / 50 / 75 / 100
 */
public class JobBonusManager {

    private static final int  DURATION_TICKS = 6 * 60 * 20;      // 6 Minuten
    private static final long COOLDOWN_MS    = 5 * 60 * 1000L;   // 5 Minuten

    private final PHSurvival plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public JobBonusManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    public void stop() {
        cooldowns.clear();
    }

    /**
     * Wird aufgerufen wenn der Spieler Shift+F drückt.
     * Gibt false zurück wenn kein Job aktiv ist oder Cooldown läuft.
     */
    public boolean tryActivate(Player player) {
        JobManager jm  = plugin.getJobManager();
        JobManager.Job job = jm.getJob(player.getUniqueId());

        if (job == null) {
            player.sendActionBar(Component.text(
                "§cKein aktiver Job! Wähle einen mit §f/jobs"));
            return false;
        }

        long now  = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = COOLDOWN_MS - (now - last);
        if (remaining > 0) {
            long secs = (remaining + 999) / 1000;
            long mins = secs / 60;
            String time = mins > 0 ? mins + "m " + (secs % 60) + "s" : secs + "s";
            player.sendActionBar(Component.text(
                "§c⏳ Job-Bonus noch in §e" + time + " §cverfügbar!"));
            return false;
        }

        int level = jm.getLevelForJob(player.getUniqueId(), job);
        List<PotionEffect> effects = bonuses(job, level);

        if (effects.isEmpty()) {
            player.sendActionBar(Component.text(
                "§eKein Bonus verfügbar – erreiche §fLevel 10 §eum Boni freizuschalten."));
            return false;
        }

        for (PotionEffect e : effects) player.addPotionEffect(e);
        cooldowns.put(player.getUniqueId(), now);

        player.sendActionBar(Component.text(
            "§a✔ §f" + job.displayName + " §aBonus aktiviert! §8(§76 min §8| §7Cooldown 5 min§8)"));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.2f);
        return true;
    }

    // ── Bonus-Tabelle ─────────────────────────────────────────────────────

    private List<PotionEffect> bonuses(JobManager.Job job, int level) {
        List<PotionEffect> list = new ArrayList<>();
        switch (job) {

            case MINER -> {
                // Lv10 Haste I → Lv25 Haste II → Lv50+Nachtblick → Lv75 Haste III → Lv100+Tempo I
                if (level >= 75)       add(list, PotionEffectType.HASTE, 2);
                else if (level >= 25)  add(list, PotionEffectType.HASTE, 1);
                else if (level >= 10)  add(list, PotionEffectType.HASTE, 0);
                if (level >= 50)  add(list, PotionEffectType.NIGHT_VISION, 0);
                if (level >= 100) add(list, PotionEffectType.SPEED, 0);
            }

            case LUMBERJACK -> {
                // Lv10 Tempo I → Lv25+Haste I → Lv50+Sprungkraft → Lv75 Tempo II → Lv100 Haste II
                if (level >= 75)       add(list, PotionEffectType.SPEED, 1);
                else if (level >= 10)  add(list, PotionEffectType.SPEED, 0);
                if (level >= 25)  add(list, PotionEffectType.HASTE, level >= 100 ? 1 : 0);
                if (level >= 50)  add(list, PotionEffectType.JUMP_BOOST, 0);
            }

            case FARMER -> {
                // Lv10 Regen I → Lv25+Tempo → Lv50+Glück I → Lv75 Regen II → Lv100 Glück II
                if (level >= 75)       add(list, PotionEffectType.REGENERATION, 1);
                else if (level >= 10)  add(list, PotionEffectType.REGENERATION, 0);
                if (level >= 25)  add(list, PotionEffectType.SPEED, 0);
                if (level >= 50)  add(list, PotionEffectType.LUCK, level >= 100 ? 1 : 0);
            }

            case HUNTER -> {
                // Lv10 Stärke I → Lv25+Tempo → Lv50+Resistenz I → Lv75 Stärke II → Lv100 Resistenz II
                if (level >= 75)       add(list, PotionEffectType.STRENGTH, 1);
                else if (level >= 10)  add(list, PotionEffectType.STRENGTH, 0);
                if (level >= 25)  add(list, PotionEffectType.SPEED, 0);
                if (level >= 50)  add(list, PotionEffectType.RESISTANCE, level >= 100 ? 1 : 0);
            }

            case FISHER -> {
                // Lv10 Glück I → Lv25+Wasseratmung → Lv50+Delfinsgnade → Lv75 Glück II → Lv100+Tempo
                if (level >= 75)       add(list, PotionEffectType.LUCK, 1);
                else if (level >= 10)  add(list, PotionEffectType.LUCK, 0);
                if (level >= 25)  add(list, PotionEffectType.WATER_BREATHING, 0);
                if (level >= 50)  add(list, PotionEffectType.DOLPHINS_GRACE, 0);
                if (level >= 100) add(list, PotionEffectType.SPEED, 0);
            }

            case BREWER -> {
                // Lv10 Regen I → Lv25+Resistenz → Lv50+Absorption I → Lv75 Regen II → Lv100 Absorption II
                if (level >= 75)       add(list, PotionEffectType.REGENERATION, 1);
                else if (level >= 10)  add(list, PotionEffectType.REGENERATION, 0);
                if (level >= 25)  add(list, PotionEffectType.RESISTANCE, 0);
                if (level >= 50)  add(list, PotionEffectType.ABSORPTION, level >= 100 ? 1 : 0);
            }

            case BUILDER -> {
                // Lv10 Haste I → Lv25+Sprungkraft → Lv50+Tempo → Lv75 Haste II → Lv100 Sprungkraft II
                if (level >= 75)       add(list, PotionEffectType.HASTE, 1);
                else if (level >= 10)  add(list, PotionEffectType.HASTE, 0);
                if (level >= 25)  add(list, PotionEffectType.JUMP_BOOST, level >= 100 ? 1 : 0);
                if (level >= 50)  add(list, PotionEffectType.SPEED, 0);
            }

            case ENCHANTER -> {
                // Lv10 Glück I → Lv25+Nachtblick → Lv50+Resistenz → Lv75 Glück II → Lv100+Absorption
                if (level >= 75)       add(list, PotionEffectType.LUCK, 1);
                else if (level >= 10)  add(list, PotionEffectType.LUCK, 0);
                if (level >= 25)  add(list, PotionEffectType.NIGHT_VISION, 0);
                if (level >= 50)  add(list, PotionEffectType.RESISTANCE, 0);
                if (level >= 100) add(list, PotionEffectType.ABSORPTION, 0);
            }

            case WEAPONSMITH -> {
                // Lv10 Stärke I → Lv25+Resistenz → Lv50+Feuerresistenz → Lv75 Stärke II → Lv100+Haste
                if (level >= 75)       add(list, PotionEffectType.STRENGTH, 1);
                else if (level >= 10)  add(list, PotionEffectType.STRENGTH, 0);
                if (level >= 25)  add(list, PotionEffectType.RESISTANCE, 0);
                if (level >= 50)  add(list, PotionEffectType.FIRE_RESISTANCE, 0);
                if (level >= 100) add(list, PotionEffectType.HASTE, 0);
            }
        }
        return list;
    }

    /** ambient=true (wenige Partikel), particles=false, icon=true (HUD-Leiste) */
    private void add(List<PotionEffect> list, PotionEffectType type, int amplifier) {
        list.add(new PotionEffect(type, DURATION_TICKS, amplifier, true, false, true));
    }
}
