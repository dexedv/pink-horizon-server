package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Job-Passivboni – manuell via Shift+F ausgelöst.
 * Cooldown: 3 Stunden | Effektdauer: 30 Minuten
 *
 * Waffenschmied: repariert Items im Inventar (kein Tränkeffekt)
 * Verzauberer:   Tränkeffekte + XP-Level-Bonus
 * Alle anderen:  passende Tränkeffekte (Meilensteine Lv 10/25/50/75/100)
 */
public class JobBonusManager {

    private static final int  DURATION_TICKS = 30 * 60 * 20;           // 30 Minuten
    private static final long COOLDOWN_MS    = 3 * 60 * 60 * 1000L;    // 3 Stunden

    // ── Waffenschmied: reparierbare Item-Tiers ─────────────────────────────
    private static final Set<Material> IRON_ITEMS = EnumSet.of(
            Material.IRON_SWORD, Material.IRON_AXE, Material.IRON_PICKAXE,
            Material.IRON_SHOVEL, Material.IRON_HOE,
            Material.IRON_HELMET, Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.SHIELD, Material.SHEARS, Material.FLINT_AND_STEEL
    );
    private static final Set<Material> GOLD_ITEMS = EnumSet.of(
            Material.GOLDEN_SWORD, Material.GOLDEN_AXE, Material.GOLDEN_PICKAXE,
            Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE,
            Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
            Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS
    );
    private static final Set<Material> DIAMOND_ITEMS = EnumSet.of(
            Material.DIAMOND_SWORD, Material.DIAMOND_AXE, Material.DIAMOND_PICKAXE,
            Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS
    );
    private static final Set<Material> NETHERITE_ITEMS = EnumSet.of(
            Material.NETHERITE_SWORD, Material.NETHERITE_AXE, Material.NETHERITE_PICKAXE,
            Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    );

    private static final long DISCOUNT_DURATION_MS = 30 * 60 * 1000L; // 30 Minuten

    private final PHSurvival plugin;
    private final Map<UUID, Long>   cooldowns       = new HashMap<>();
    /** UUID → [rabattProzent, ablaufZeitstempel] */
    private final Map<UUID, long[]> enchantDiscounts = new HashMap<>();

    public JobBonusManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    public void stop() {
        cooldowns.clear();
        enchantDiscounts.clear();
    }

    /**
     * Gibt den aktiven Verzauberungs-Rabatt (0–100 %) zurück.
     * 0 = kein aktiver Rabatt.
     */
    public int getEnchantDiscount(UUID uuid) {
        long[] data = enchantDiscounts.get(uuid);
        if (data == null) return 0;
        if (System.currentTimeMillis() > data[1]) {
            enchantDiscounts.remove(uuid);
            return 0;
        }
        return (int) data[0];
    }

    // ── Öffentliche API ───────────────────────────────────────────────────

    /** Wird von JobBonusListener bei Shift+F aufgerufen. */
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
            player.sendActionBar(Component.text(
                "§c⏳ Job-Bonus noch in §e" + formatTime(remaining) + " §cverfügbar!"));
            return false;
        }

        int level = jm.getLevelForJob(player.getUniqueId(), job);
        boolean success;

        if (job == JobManager.Job.WEAPONSMITH) {
            success = activateWeaponsmith(player, level);
        } else {
            success = activateEffects(player, job, level);
        }

        if (success) {
            cooldowns.put(player.getUniqueId(), now);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.2f);
        }
        return success;
    }

    // ── Waffenschmied: kostenlose Reparatur ───────────────────────────────

    private boolean activateWeaponsmith(Player player, int level) {
        if (level < 25) {
            player.sendActionBar(Component.text(
                "§eErreiche §fLevel 25 §eum kostenlose Reparaturen freizuschalten."));
            return false;
        }

        Set<Material> repairable = EnumSet.noneOf(Material.class);
        if (level >= 25)  repairable.addAll(IRON_ITEMS);
        if (level >= 50)  repairable.addAll(GOLD_ITEMS);
        if (level >= 75)  repairable.addAll(DIAMOND_ITEMS);
        if (level >= 100) repairable.addAll(NETHERITE_ITEMS);

        int repaired = 0;
        repaired += repairSlots(player.getInventory().getContents(), repairable);
        repaired += repairSlots(player.getInventory().getArmorContents(), repairable);
        repaired += repairSlot(player.getInventory().getItemInOffHand(), repairable);

        if (repaired == 0) {
            player.sendActionBar(Component.text(
                "§eKeine beschädigten Items gefunden. §8(Kein Cooldown verbraucht)"));
            return false;   // Cooldown wird NICHT verbraucht
        }

        String tier = level >= 100 ? "Netherit/Diamant/Gold/Eisen"
                    : level >= 75  ? "Diamant/Gold/Eisen"
                    : level >= 50  ? "Gold/Eisen"
                    : "Eisen";
        player.sendActionBar(Component.text(
            "§a✔ §f" + repaired + " Items §a(" + tier + ") kostenlos repariert! §8(§73h Cooldown§8)"));
        return true;
    }

    private int repairSlots(ItemStack[] slots, Set<Material> repairable) {
        int count = 0;
        for (ItemStack item : slots) count += repairSlot(item, repairable);
        return count;
    }

    private int repairSlot(ItemStack item, Set<Material> repairable) {
        if (item == null || item.getType() == Material.AIR) return 0;
        if (!repairable.contains(item.getType())) return 0;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmg) || dmg.getDamage() == 0) return 0;
        dmg.setDamage(0);
        item.setItemMeta(meta);
        return 1;
    }

    // ── Alle anderen Jobs: Tränkeffekte (+ XP für Verzauberer) ───────────

    private boolean activateEffects(Player player, JobManager.Job job, int level) {
        List<PotionEffect> effects = bonuses(job, level);

        if (effects.isEmpty() && job != JobManager.Job.ENCHANTER) {
            player.sendActionBar(Component.text(
                "§eKein Bonus verfügbar – erreiche §fLevel 10 §eum Boni freizuschalten."));
            return false;
        }

        for (PotionEffect e : effects) player.addPotionEffect(e);

        // Verzauberer: Rabatt auf Verzauberungskosten setzen
        if (job == JobManager.Job.ENCHANTER) {
            int discount = level >= 100 ? 75 : level >= 75 ? 55 : level >= 50 ? 40 : level >= 25 ? 25 : 0;
            if (effects.isEmpty() && discount == 0) {
                player.sendActionBar(Component.text(
                    "§eKein Bonus verfügbar – erreiche §fLevel 10 §eum Boni freizuschalten."));
                return false;
            }
            if (discount > 0) {
                long expiry = System.currentTimeMillis() + DISCOUNT_DURATION_MS;
                enchantDiscounts.put(player.getUniqueId(), new long[]{discount, expiry});
                player.sendActionBar(Component.text(
                    "§a✔ §fVerzauberer §aBonus: §d" + discount + "% §aVerzauberungs-Rabatt §8(§730 min §8| §73h Cooldown§8)"));
            } else {
                player.sendActionBar(Component.text(
                    "§a✔ §fVerzauberer §aBonus aktiviert! §8(§730 min §8| §73h Cooldown§8)"));
            }
            return true;
        }

        if (effects.isEmpty()) {
            player.sendActionBar(Component.text(
                "§eKein Bonus verfügbar – erreiche §fLevel 10 §eum Boni freizuschalten."));
            return false;
        }

        player.sendActionBar(Component.text(
            "§a✔ §f" + job.displayName + " §aBonus aktiviert! §8(§730 min §8| §73h Cooldown§8)"));
        return true;
    }

    // ── Bonus-Definitionen ────────────────────────────────────────────────

    private List<PotionEffect> bonuses(JobManager.Job job, int level) {
        List<PotionEffect> list = new ArrayList<>();
        switch (job) {

            case MINER -> {
                // Haste + Nachtblick + Glück (bessere Erz-Drops) + Tempo
                if (level >= 75)       add(list, PotionEffectType.HASTE, 2);
                else if (level >= 25)  add(list, PotionEffectType.HASTE, 1);
                else if (level >= 10)  add(list, PotionEffectType.HASTE, 0);
                if (level >= 50)  add(list, PotionEffectType.NIGHT_VISION, 0);
                if (level >= 75)  add(list, PotionEffectType.LUCK, 0);
                if (level >= 100) {
                    add(list, PotionEffectType.LUCK, 1);
                    add(list, PotionEffectType.SPEED, 0);
                }
            }

            case LUMBERJACK -> {
                // Tempo (schnell durch Wälder) + Haste (Bäume fällen) + Sprungkraft
                if (level >= 75)       add(list, PotionEffectType.SPEED, 1);
                else if (level >= 10)  add(list, PotionEffectType.SPEED, 0);
                if (level >= 25)  add(list, PotionEffectType.HASTE, level >= 100 ? 1 : 0);
                if (level >= 50)  add(list, PotionEffectType.JUMP_BOOST, 0);
            }

            case FARMER -> {
                // Regeneration (gutes Essen) + Tempo + Glück (seltene Drops)
                if (level >= 75)       add(list, PotionEffectType.REGENERATION, 1);
                else if (level >= 10)  add(list, PotionEffectType.REGENERATION, 0);
                if (level >= 25)  add(list, PotionEffectType.SPEED, 0);
                if (level >= 50)  add(list, PotionEffectType.LUCK, level >= 100 ? 1 : 0);
            }

            case HUNTER -> {
                // Stärke + Tempo + Resistenz – Kampffokus
                if (level >= 75)       add(list, PotionEffectType.STRENGTH, 1);
                else if (level >= 10)  add(list, PotionEffectType.STRENGTH, 0);
                if (level >= 25)  add(list, PotionEffectType.SPEED, 0);
                if (level >= 50)  add(list, PotionEffectType.RESISTANCE, level >= 100 ? 1 : 0);
            }

            case FISHER -> {
                // Glück (bessere Fänge) + Wasseratmung + Delfinsgnade
                if (level >= 75)       add(list, PotionEffectType.LUCK, 1);
                else if (level >= 10)  add(list, PotionEffectType.LUCK, 0);
                if (level >= 25)  add(list, PotionEffectType.WATER_BREATHING, 0);
                if (level >= 50)  add(list, PotionEffectType.DOLPHINS_GRACE, 0);
                if (level >= 100) add(list, PotionEffectType.SPEED, 0);
            }

            case BREWER -> {
                // Regeneration + Resistenz + Absorption – kennt alle Tränke
                if (level >= 75)       add(list, PotionEffectType.REGENERATION, 1);
                else if (level >= 10)  add(list, PotionEffectType.REGENERATION, 0);
                if (level >= 25)  add(list, PotionEffectType.RESISTANCE, 0);
                if (level >= 50)  add(list, PotionEffectType.ABSORPTION, level >= 100 ? 1 : 0);
            }

            case BUILDER -> {
                // Haste + Sprungkraft (Gerüste) + Tempo
                if (level >= 75)       add(list, PotionEffectType.HASTE, 1);
                else if (level >= 10)  add(list, PotionEffectType.HASTE, 0);
                if (level >= 25)  add(list, PotionEffectType.JUMP_BOOST, level >= 100 ? 1 : 0);
                if (level >= 50)  add(list, PotionEffectType.SPEED, 0);
            }

            case ENCHANTER -> {
                // Glück (bessere Verzauberungen) + Nachtblick (Verzauberungstisch)
                // XP-Level-Bonus wird separat in activateEffects() vergeben
                if (level >= 75)       add(list, PotionEffectType.LUCK, 1);
                else if (level >= 10)  add(list, PotionEffectType.LUCK, 0);
                if (level >= 25)  add(list, PotionEffectType.NIGHT_VISION, 0);
            }

            // WEAPONSMITH hat keine Tränkeffekte → eigene Methode
            default -> {}
        }
        return list;
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────

    /** ambient=true (kaum Partikel), particles=false, icon=true (HUD) */
    private void add(List<PotionEffect> list, PotionEffectType type, int amplifier) {
        list.add(new PotionEffect(type, DURATION_TICKS, amplifier, true, false, true));
    }

    private String formatTime(long ms) {
        long secs  = (ms + 999) / 1000;
        long hours = secs / 3600;
        long mins  = (secs % 3600) / 60;
        long s     = secs % 60;
        if (hours > 0) return hours + "h " + mins + "m " + s + "s";
        if (mins  > 0) return mins + "m " + s + "s";
        return s + "s";
    }
}
