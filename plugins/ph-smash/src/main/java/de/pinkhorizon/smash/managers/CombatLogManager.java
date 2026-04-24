package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.arena.ArenaInstance;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zentrales Chat-System für das Kampfgeschehen:
 * Fähigkeits-Procs mit Schadensberechnung, Boss-Phasen, Angriffe, Live-HUD.
 */
public class CombatLogManager {

    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacySection();

    private final PHSmash plugin;

    // Cooldowns für Boss-Modifier-Meldungen (verhindert Spam bei häufigen Treffern)
    private final Map<UUID, Long> cdModPoison  = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cdModBurn    = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cdModWither  = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cdModSlow    = new ConcurrentHashMap<>();
    private static final long     MOD_CD_MS    = 3_000L;

    public CombatLogManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    // ── Zahlen-Format ────────────────────────────────────────────────────────

    public static String fmt(double dmg) {
        if (dmg >= 1_000_000) return String.format("%.2fM", dmg / 1_000_000.0);
        if (dmg >= 1_000)     return String.format("%.1fK", dmg / 1_000.0);
        if (dmg >= 10)        return String.valueOf((int) Math.round(dmg));
        return String.format("%.1f", dmg);
    }

    // ── Fähigkeits-Procs ─────────────────────────────────────────────────────

    /**
     * Einzel-Proc mit Bonus-Schaden und Formel.
     * Beispiel: Kritischer Treffer ×2.5 → +1,234 Schaden
     */
    public void proc(Player player, String icon, String color,
                     String name, String formula, double finalBonusDmg) {
        player.sendMessage(
            "§8[" + color + icon + "§8] " + color + "§l" + name
            + "  §8│  §7" + formula + "  §8⟶  §f+" + fmt(finalBonusDmg) + " §7Schaden"
        );
    }

    /**
     * DOT-Proc: Schaden pro Tick × Ticks = Gesamtschaden.
     * Beispiel: Blutung  156 × 8 Ticks = 1,248 gesamt
     */
    public void dot(Player player, String icon, String color,
                    String name, double finalTickDmg, int ticks) {
        double total = finalTickDmg * ticks;
        player.sendMessage(
            "§8[" + color + icon + "§8] " + color + "§l" + name
            + "  §8│  §f" + fmt(finalTickDmg) + " §7× §f" + ticks
            + " §7Ticks  §8=  " + color + "§l" + fmt(total) + " §7gesamt"
        );
    }

    // ── Defensiv-Events ──────────────────────────────────────────────────────

    public void dodge(Player player, double chance) {
        player.sendMessage(
            "§8[§b⚡§8] §b§lAusgewichen!  §8│  §7Dodge-Chance §b" + (int)(chance * 100) + "%"
        );
    }

    public void resist(Player player, double chance) {
        player.sendMessage(
            "§8[§d🛡§8] §d§lEffekt resistiert!  §8│  §7Resist-Chance §d" + (int)(chance * 100) + "%"
        );
    }

    public void reflect(Player player, double reflectedDmg) {
        player.sendMessage(
            "§8[§c↩§8] §c§lSchaden-Reflektion  §8│  §c-" + fmt(reflectedDmg) + " ❤  §8(GESPIEGELT)"
        );
    }

    // ── Boss-Modifier-Treffer (3s-Cooldown gegen Spam) ──────────────────────

    public void modifierPoison(Player player) {
        if (!checkCd(player.getUniqueId(), cdModPoison)) return;
        player.sendMessage("§8[§2☠§8] §2Boss-Gift  §8│  §2Poison II §7für §f3s  §8(§7VERGIFTET§8)");
    }

    public void modifierBurn(Player player) {
        if (!checkCd(player.getUniqueId(), cdModBurn)) return;
        player.sendMessage("§8[§6🔥§8] §6Boss-Flammen  §8│  §6Brennend §7für §f4s  §8(§7BRENNEND§8)");
    }

    public void modifierWither(Player player) {
        if (!checkCd(player.getUniqueId(), cdModWither)) return;
        player.sendMessage("§8[§5💀§8] §5Verdorrend  §8│  §5Wither I §7für §f4s  §8(§7VERDORREND§8)");
    }

    public void modifierSlow(Player player) {
        if (!checkCd(player.getUniqueId(), cdModSlow)) return;
        player.sendMessage("§8[§9❄§8] §9Verlangsamung  §8│  §9Slowness III §7für §f2s  §8(§7VERLANGSAMEND§8)");
    }

    private boolean checkCd(UUID uuid, Map<UUID, Long> cdMap) {
        long now = System.currentTimeMillis();
        if (now - cdMap.getOrDefault(uuid, 0L) < MOD_CD_MS) return false;
        cdMap.put(uuid, now);
        return true;
    }

    // ── Boss-Spezialangriffe ─────────────────────────────────────────────────

    public void bossSlam(Player player, double dmg) {
        player.sendMessage(
            "§8[§c⚡§8] §c§lBoss-Slam!  §8│  §c-" + fmt(dmg) + " ❤ §7Schaden erhalten"
        );
    }

    public void bossPoison(Player player) {
        player.sendMessage(
            "§8[§2☠§8] §2§lBoss vergiftet dich!  §8│  §2Poison I §7für §f3s"
        );
    }

    public void bossSlow(Player player) {
        player.sendMessage(
            "§8[§9❄§8] §9§lBoss verlangsamt dich!  §8│  §9Slowness II §7für §f3s"
        );
    }

    public void bossExplosion(Player player, double dmg) {
        player.sendMessage(
            "§8[§c💥§8] §c§lExplosion!  §8│  §c-" + fmt(dmg) + " ❤ §7Schaden erhalten"
        );
    }

    // ── Boss-Phasen (mit Trennlinie) ─────────────────────────────────────────

    public void phase75(Player player) {
        player.sendMessage("§8§m─────────────────────────────────");
        player.sendMessage("§8[§e⚡§8] §e§lBoss-Phase 75%  §8│  §7Der Boss wird §ewütend§7!");
        player.sendMessage("§8§m─────────────────────────────────");
    }

    public void phase50(Player player) {
        player.sendMessage("§8§m═════════════════════════════════");
        player.sendMessage("§8[§c☠§8] §c§l⚠ RAGE-MODUS  §8│  §c+50% Schaden §7für §f30s§7!");
        player.sendMessage("§8§m═════════════════════════════════");
    }

    public void phase25(Player player, double healAmount) {
        player.sendMessage("§8§m─────────────────────────────────");
        player.sendMessage("§8[§c☠§8] §c§lBoss-Phase 25%  §8│  §7Heilt §c+" + fmt(healAmount)
            + " HP  §8│  §cMeteorregen§7!");
        player.sendMessage("§8§m─────────────────────────────────");
    }

    // ── Boss besiegt – Zusammenfassung ───────────────────────────────────────

    public void bossDefeated(Player player, int level, int nextLevel,
                             double sessionDamage, long coins,
                             int streak, double streakMulti, long fightMs) {
        String timeStr = fightMs >= 60_000
            ? String.format("%dm %ds", fightMs / 60_000, (fightMs % 60_000) / 1_000)
            : String.format("%.1fs", fightMs / 1_000.0);
        boolean fastKill = fightMs <= 90_000;

        player.sendMessage("§8§m══════════════════════════════════════");
        player.sendMessage("§6§l★ BOSS BESIEGT!  §8│  §7Level §c" + level + " §8⟶ §a" + nextLevel);
        player.sendMessage("§8§m──────────────────────────────────────");
        player.sendMessage("  §7Gesamt-Schaden  §8│  §f§l" + fmt(sessionDamage));
        player.sendMessage("  §7Münzen          §8│  §e§l+" + coins);
        if (streak >= 2) {
            int bonusPct = (int)((streakMulti - 1.0) * 100);
            player.sendMessage("  §7Streak          §8│  §6§l" + streak + "x  §8(§7+" + bonusPct + "% Schaden§8)");
        }
        player.sendMessage("  §7Kampfzeit       §8│  §f" + timeStr + (fastKill ? "  §a§l⚡ Schnellkill!" : ""));
        player.sendMessage("§8§m══════════════════════════════════════");
    }

    // ── Action-Bar Live-HUD ──────────────────────────────────────────────────

    /**
     * Zeigt nach jedem Treffer den aktuellen Schadens-Multiplikator
     * und die verbleibende Boss-HP im Action-Bar an.
     */
    public void updateActionBar(Player player) {
        UUID uuid = player.getUniqueId();

        double upg      = plugin.getUpgradeManager().getAttackMultiplier(uuid);
        double prestige = plugin.getPrestigeManager().getPrestigeMultiplier(uuid);
        double streak   = plugin.getStreakManager().getStreakMultiplier(uuid);
        double combo    = plugin.getComboManager().getMultiplier(uuid);
        double rune     = plugin.getRuneManager().getWarRuneMultiplier(uuid);
        double total    = upg * prestige * streak * combo * rune;

        // Berserker-Status
        String berserker = "";
        double bersBonus = plugin.getAbilityManager().getBerserkerBonus(uuid);
        if (bersBonus > 0) {
            var hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (hpAttr != null && player.getHealth() < hpAttr.getValue() * 0.35) {
                berserker = "  §c⚡§7Berserker+" + (int)(bersBonus * 100) + "%";
            }
        }

        // Boss-HP
        ArenaInstance arena = plugin.getArenaManager().getArena(uuid);
        String bossInfo = "";
        if (arena != null) {
            int bossPct = (int)(arena.getHpPercent() * 100);
            String hpColor = bossPct > 50 ? "§a" : bossPct > 25 ? "§e" : "§c";
            bossInfo = "  §8│  §7Boss " + hpColor + bossPct + "§7%  §8(" + fmt(arena.getCurrentHp()) + " §7HP§8)";
        }

        String bar = String.format(
            "§7⚔ §f%.2f× §8[§7Upg §f%.1f §8│ §6Str §f%.2f §8│ §eCmb §f%.2f §8│ §dRune §f%.1f§8]%s%s",
            total, upg, streak, combo, rune, berserker, bossInfo
        );

        player.sendActionBar(LEG.deserialize(bar));
    }
}
