package de.pinkhorizon.survival.commands;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.JobManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /jobs – Öffnet das Job-GUI.
 *
 * Layout (54 Slots):
 *   Zeile 0+5  : Gold-Rahmen
 *   Zeile 1–3  : 3×3 Job-Items (Slots 11,13,15 / 20,22,24 / 29,31,33)
 *   Zeile 4    : Status-Item (39) | Bonus-Item (41)
 */
public class JobsCommand implements CommandExecutor {

    public static final String JOB_KEY = "job_id";

    private final PHSurvival plugin;

    public JobsCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler!");
            return true;
        }
        openGui(player);
        return true;
    }

    public void openGui(Player player) {
        UUID uuid        = player.getUniqueId();
        JobManager jm    = plugin.getJobManager();
        JobManager.Job   current    = jm.getJob(uuid);
        long             cooldownMs = plugin.getJobBonusManager().getCooldownRemainingMs(uuid);

        Inventory inv = plugin.getServer().createInventory(null, 54,
                Component.text("§6§lJobs §8| §ePink Horizon"));

        // Rahmen
        ItemStack border = glass(Material.GRAY_STAINED_GLASS_PANE, "§8 ");
        ItemStack gold   = glass(Material.GOLD_BLOCK,              "§6 ");
        for (int i =  0; i <  9; i++) inv.setItem(i, gold);
        for (int i = 45; i < 54; i++) inv.setItem(i, gold);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     border);
            inv.setItem(row * 9 + 8, border);
            for (int col = 1; col <= 7; col++) inv.setItem(row * 9 + col, border);
        }

        // 9 Job-Items  3 × 3
        int[] slots = { 11, 13, 15, 20, 22, 24, 29, 31, 33 };
        JobManager.Job[] jobs = JobManager.Job.values();
        for (int i = 0; i < jobs.length && i < slots.length; i++) {
            inv.setItem(slots[i], makeJobItem(jobs[i], current, uuid, jm, cooldownMs));
        }

        // Status + Bonus
        inv.setItem(39, makeStatusItem(current, uuid, jm));
        inv.setItem(41, makeBonusItem(current, uuid, jm, cooldownMs));

        player.openInventory(inv);
    }

    // ── Job-Item ──────────────────────────────────────────────────────────

    private ItemStack makeJobItem(JobManager.Job job, JobManager.Job current,
                                  UUID uuid, JobManager jm, long cooldownMs) {
        boolean isActive = job == current;
        ItemStack item   = new ItemStack(job.icon);
        ItemMeta  meta   = item.getItemMeta();

        meta.displayName(Component.text(
            (isActive ? "§a§l" : "§e§l") + job.displayName
            + (isActive ? " §8[§aAKTIV§8]" : "")));

        List<Component> lore = new ArrayList<>();
        lore.add(line("§8" + "─".repeat(30)));
        lore.add(line(job.description, NamedTextColor.GRAY));
        lore.add(empty());

        int level    = jm.getLevelForJob(uuid, job);
        int maxLevel = JobManager.getMaxLevel();

        if (isActive) {
            int xp   = jm.getXp(uuid);
            int next = JobManager.xpForNextLevel(level);

            lore.add(line("§7Level:  §6" + level + " §8/ §6" + maxLevel));
            lore.add(line("§8" + xpBar(xp, next, 14)));
            lore.add(line(next > 0
                ? "§7XP: §b" + fmt(xp) + " §8/ §b" + fmt(next)
                : "§6§l★ MAX LEVEL ★"));
            lore.add(line("§7Münz-Bonus: §6+" + bonusPct(level) + "% §8(×" + bonusMult(level) + ")"));
            lore.add(empty());

            // Bonus-Status
            lore.add(line("§b§l✦ Shift+F Bonus:"));
            lore.add(line(cooldownMs <= 0
                ? "  §a✔ Bereit zur Aktivierung!"
                : "  §c⏳ Cooldown: §f" + fmtTime(cooldownMs)));
            String desc = currentBonusDesc(job, level);
            if (desc != null) for (String ln : desc.split("\n")) lore.add(line("  §7" + ln));
            lore.add(empty());

            // Nächster Meilenstein
            String next2 = nextMilestone(job, level);
            lore.add(next2 != null
                ? line("§e⭐ " + next2)
                : line("§6§l🏆 Maximales Level erreicht!"));
            lore.add(empty());
            lore.add(line("§c▶ Klicken zum Verlassen"));

        } else {
            // Bisheriges Level anzeigen
            if (level > 1) {
                lore.add(line("§7Erreichtes Level: §e" + level + " §8/ §e" + maxLevel));
                lore.add(line("§7Münz-Bonus: §6+" + bonusPct(level) + "%"));
                lore.add(empty());
            } else {
                lore.add(line("§8Noch nicht gespielt."));
                lore.add(empty());
            }

            // Alle Meilensteine auflisten
            lore.add(line("§e§l✦ Boni-Meilensteine:"));
            int[]    ms    = milestoneLevels(job);
            String[] descs = milestoneDescs(job);
            if (ms != null && descs != null) {
                for (int i = 0; i < Math.min(ms.length, descs.length); i++) {
                    boolean reached = level >= ms[i];
                    lore.add(line((reached ? "§a✔" : "§8○")
                        + " §7Lv." + ms[i] + ": §f" + descs[i]));
                }
            }
            lore.add(empty());
            lore.add(line("§a▶ Klicken zum Beitreten"));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, JOB_KEY), PersistentDataType.STRING, job.name());
        item.setItemMeta(meta);
        return item;
    }

    // ── Status-Item (Slot 39) ────────────────────────────────────────────

    private ItemStack makeStatusItem(JobManager.Job current, UUID uuid, JobManager jm) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("§e§lMein Job-Überblick"));

        List<Component> lore = new ArrayList<>();
        lore.add(line("§8" + "─".repeat(30)));

        if (current != null) {
            int level = jm.getLevel(uuid);
            int xp    = jm.getXp(uuid);
            int next  = JobManager.xpForNextLevel(level);
            int pct   = next > 0 ? (int) Math.round((double) xp / next * 100) : 100;

            lore.add(line("§7Aktiver Job: §f§l" + current.displayName));
            lore.add(line("§7Level: §6" + level + " §8/ §6" + JobManager.getMaxLevel()));
            lore.add(line("§8" + xpBar(xp, next, 18)));
            lore.add(line(next > 0
                ? "§7XP: §b" + fmt(xp) + " §8/ §b" + fmt(next) + " §8(§f" + pct + "%§8)"
                : "§6§l★ MAX LEVEL ★"));
            lore.add(line("§7Münz-Bonus: §6+" + bonusPct(level) + "%  §8(×" + bonusMult(level) + ")"));
            lore.add(empty());

            // Andere Jobs mit Fortschritt
            boolean hasOther = false;
            for (JobManager.Job j : JobManager.Job.values()) {
                if (j == current) continue;
                int lv = jm.getLevelForJob(uuid, j);
                if (lv > 1) {
                    if (!hasOther) { lore.add(line("§7Weitere Jobs:")); hasOther = true; }
                    lore.add(line("  §8" + j.displayName + ": §7Lv." + lv
                        + "  §8(+" + bonusPct(lv) + "%)"));
                }
            }
            if (!hasOther) lore.add(line("§8Keine weiteren Job-Fortschritte."));
        } else {
            lore.add(line("§cKein aktiver Job!"));
            lore.add(line("§7Klicke auf einen Job, um ihn anzunehmen."));
        }

        lore.add(empty());
        lore.add(line("§8Du kannst jederzeit den Job wechseln."));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Bonus-Item (Slot 41) ─────────────────────────────────────────────

    private ItemStack makeBonusItem(JobManager.Job current, UUID uuid, JobManager jm, long cooldownMs) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("§b§l✦ Shift+F Bonus-Info"));

        List<Component> lore = new ArrayList<>();
        lore.add(line("§8" + "─".repeat(30)));

        if (current != null) {
            int level = jm.getLevel(uuid);

            // Cooldown-Status
            if (cooldownMs <= 0) {
                lore.add(line("§a§l✔ BONUS BEREIT!"));
                lore.add(line("§7Drücke §fShift+F §7zum Aktivieren."));
            } else {
                lore.add(line("§c⏳ Cooldown: §f" + fmtTime(cooldownMs)));
                lore.add(line("§7Bonus noch nicht verfügbar."));
            }
            lore.add(empty());

            // Aktuelle Effekte
            lore.add(line("§7Aktuelle Effekte §8(Lv." + level + ")§7:"));
            String desc = currentBonusDesc(current, level);
            if (desc != null) {
                for (String ln : desc.split("\n")) lore.add(line("  §b» §f" + ln));
            } else {
                lore.add(line("  §8Kein Bonus – erreiche §7Lv.10§8."));
            }
            lore.add(empty());

            // Nächster Meilenstein
            String next = nextMilestone(current, level);
            if (next != null) lore.add(line("§e⭐ " + next));

            lore.add(empty());
            lore.add(line("§8Cooldown:  §73 Stunden"));
            lore.add(line("§8Dauer:     §76 Minuten"));
        } else {
            lore.add(line("§cKein aktiver Job!"));
            lore.add(line("§7Wähle einen Job für Boni."));
            lore.add(empty());
            lore.add(line("§8Drücke §7Shift+F §8um den Bonus"));
            lore.add(line("§8deines aktiven Jobs zu aktivieren."));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Bonus-Beschreibungen ─────────────────────────────────────────────

    private static String currentBonusDesc(JobManager.Job job, int level) {
        return switch (job) {
            case MINER -> {
                if (level < 10) yield null;
                yield level >= 100 ? "Haste III + Nachtblick + Glück II + Tempo I"
                    : level >= 75  ? "Haste III + Nachtblick + Glück I"
                    : level >= 50  ? "Haste II + Nachtblick"
                    : level >= 25  ? "Haste II"
                    :                "Haste I";
            }
            case LUMBERJACK -> {
                if (level < 10) yield null;
                yield level >= 100 ? "Tempo II + Haste II + Sprungkraft I"
                    : level >= 75  ? "Tempo II + Haste I + Sprungkraft I"
                    : level >= 50  ? "Tempo I + Haste I + Sprungkraft I"
                    : level >= 25  ? "Tempo I + Haste I"
                    :                "Tempo I";
            }
            case FARMER -> {
                if (level < 10) yield null;
                yield level >= 100 ? "Regeneration II + Tempo I + Glück II"
                    : level >= 75  ? "Regeneration II + Tempo I + Glück I"
                    : level >= 50  ? "Regeneration I + Tempo I + Glück I"
                    : level >= 25  ? "Regeneration I + Tempo I"
                    :                "Regeneration I";
            }
            case HUNTER -> {
                if (level < 10) yield null;
                yield level >= 100 ? "Stärke II + Tempo I + Resistenz II"
                    : level >= 75  ? "Stärke II + Tempo I + Resistenz I"
                    : level >= 50  ? "Stärke I + Tempo I + Resistenz I"
                    : level >= 25  ? "Stärke I + Tempo I"
                    :                "Stärke I";
            }
            case FISHER -> {
                if (level < 10) yield null;
                int lure = level >= 100 ? 3 : level >= 50 ? 2 : 1;
                int luck = level >= 75  ? 3 : level >= 25 ? 2 : 1;
                String effects = level >= 100 ? "Glück II + Wasseratmung + Delfinsgnade + Tempo I"
                    : level >= 75              ? "Glück II + Wasseratmung + Delfinsgnade"
                    : level >= 50              ? "Glück I + Wasseratmung + Delfinsgnade"
                    : level >= 25              ? "Glück I + Wasseratmung"
                    :                            "Glück I";
                yield effects + "\nBonus-Angel: Köder " + roman(lure) + " + Glück d. Meeres " + roman(luck);
            }
            case BREWER -> {
                if (level < 10) yield null;
                int potions = level >= 100 ? 3 : level >= 65 ? 2 : level >= 25 ? 1 : 0;
                String effects = level >= 100 ? "Regeneration II + Resistenz I + Absorption II"
                    : level >= 75              ? "Regeneration II + Resistenz I + Absorption I"
                    : level >= 50              ? "Regeneration I + Resistenz I + Absorption I"
                    : level >= 25              ? "Regeneration I + Resistenz I"
                    :                            "Regeneration I";
                yield potions > 0 ? effects + "\n+" + potions + " zufällige Extra-Tränke" : effects;
            }
            case BUILDER -> {
                if (level < 10) yield null;
                int mins = level >= 100 ? 6 : level >= 75 ? 5 : level >= 50 ? 3 : level >= 25 ? 2 : 1;
                String effects = level >= 100 ? "Haste II + Sprungkraft II + Tempo I"
                    : level >= 75              ? "Haste II + Sprungkraft I"
                    : level >= 50              ? "Haste I + Sprungkraft I + Tempo I"
                    : level >= 25              ? "Haste I + Sprungkraft I"
                    :                            "Haste I";
                yield effects + "\n" + mins + " Minuten Fliegen";
            }
            case ENCHANTER -> {
                if (level < 10) yield null;
                int discount = level >= 100 ? 75 : level >= 75 ? 55 : level >= 50 ? 40 : level >= 25 ? 25 : 0;
                String effects = level >= 75 ? "Glück II + Nachtblick" : "Glück I" + (level >= 25 ? " + Nachtblick" : "");
                yield discount > 0 ? effects + "\n" + discount + "% XP-Rabatt beim Verzaubern" : effects;
            }
            case WEAPONSMITH -> {
                if (level < 25) yield "§8Kein Bonus – erreiche Lv.25.";
                yield "Kostenlose Reparatur: " + (level >= 100 ? "Netherit/Diamant/Gold/Eisen"
                    : level >= 75 ? "Diamant/Gold/Eisen"
                    : level >= 50 ? "Gold/Eisen"
                    : "Eisen");
            }
        };
    }

    private static int[] milestoneLevels(JobManager.Job job) {
        return switch (job) {
            case BREWER     -> new int[]{10, 25, 50, 65, 75, 100};
            case WEAPONSMITH-> new int[]{25, 50, 75, 100};
            default         -> new int[]{10, 25, 50, 75, 100};
        };
    }

    private static String[] milestoneDescs(JobManager.Job job) {
        return switch (job) {
            case MINER       -> new String[]{"Haste I","Haste II","Nachtblick","Haste III + Glück I","Glück II + Tempo I"};
            case LUMBERJACK  -> new String[]{"Tempo I","Haste I","Sprungkraft I","Tempo II","Haste II"};
            case FARMER      -> new String[]{"Regeneration I","Tempo I","Glück I","Regeneration II","Glück II"};
            case HUNTER      -> new String[]{"Stärke I","Tempo I","Resistenz I","Stärke II","Resistenz II"};
            case FISHER      -> new String[]{"Glück I + Bonus-Angel","Wasseratmung + Bonus-Angel","Delfinsgnade + Bonus-Angel","Glück II + Bonus-Angel","Tempo I + Bonus-Angel"};
            case BREWER      -> new String[]{"Regeneration I","Resistenz I + 1 Extra-Trank","Absorption I","2 Extra-Tränke","Regeneration II","Absorption II + 3 Tränke"};
            case BUILDER     -> new String[]{"Haste I + 1 min Fly","Sprungkraft I + 2 min Fly","Tempo I + 3 min Fly","Haste II + 5 min Fly","Sprungkraft II + 6 min Fly"};
            case ENCHANTER   -> new String[]{"Glück I","Nachtblick + 25% XP-Rabatt","40% XP-Rabatt","Glück II + 55% XP-Rabatt","75% XP-Rabatt"};
            case WEAPONSMITH -> new String[]{"Eisen-Reparatur","Gold + Eisen","Diamant/Gold/Eisen","Alle Tiers"};
        };
    }

    private static String nextMilestone(JobManager.Job job, int level) {
        int[]    lvls  = milestoneLevels(job);
        String[] descs = milestoneDescs(job);
        if (lvls == null || descs == null) return null;
        for (int i = 0; i < lvls.length; i++) {
            if (level < lvls[i]) {
                int remaining = lvls[i] - level;
                return "Nächster Meilenstein: §fLv." + lvls[i]
                    + " §8(§7noch " + remaining + " Level§8) §7→ §f" + descs[i];
            }
        }
        return null;
    }

    // ── Formatierungshelfer ──────────────────────────────────────────────

    private static String xpBar(int xp, int needed, int len) {
        if (needed <= 0) return "§6§l★ MAX LEVEL ★";
        int filled = (int) Math.min(len, Math.round((double) xp / needed * len));
        return "§a" + "█".repeat(filled) + "§8" + "░".repeat(len - filled);
    }

    private static int bonusPct(int level) {
        return (int) ((JobManager.getMultiplier(level) - 1.0) * 100);
    }

    private static String bonusMult(int level) {
        return String.format("%.2f", JobManager.getMultiplier(level));
    }

    private static String fmt(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private static String fmtTime(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? h + "h " + m + "m" : m > 0 ? m + "m " + sec + "s" : sec + "s";
    }

    private static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> String.valueOf(n); };
    }

    private static Component line(String text) { return Component.text(text); }
    private static Component line(String text, NamedTextColor c) { return Component.text(text, c); }
    private static Component empty() { return Component.text(""); }

    private static ItemStack glass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }
}
