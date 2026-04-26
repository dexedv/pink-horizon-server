package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Verwaltet den täglichen Login-Bonus mit Streak-System.
 */
public class DailyBonusManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Belohnungen für Tag 1–7 (Tag 8+ = wiederholt Tag 7) */
    private static final long[] DAILY_REWARDS = {
        10_000L, 25_000L, 50_000L, 100_000L, 200_000L, 350_000L, 500_000L
    };

    public DailyBonusManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    /**
     * Prüft beim Join ob ein Tagesbonus fällig ist und schreibt ihn gut.
     */
    public void checkDailyBonus(Player player, PlayerData data) {
        long nowTs = System.currentTimeMillis() / 1000;
        long lastDaily = data.getLastDaily();

        LocalDate today = LocalDate.now();
        LocalDate lastClaimDate = lastDaily == 0 ? null
                : Instant.ofEpochSecond(lastDaily).atZone(ZoneId.systemDefault()).toLocalDate();

        // Bereits heute beansprucht?
        if (lastClaimDate != null && lastClaimDate.equals(today)) return;

        // Streak aktualisieren
        if (lastClaimDate != null && lastClaimDate.plusDays(1).equals(today)) {
            data.setDailyStreak(data.getDailyStreak() + 1);
        } else {
            // Streak-Reset (erste Anmeldung oder Unterbrechung)
            data.setDailyStreak(1);
        }

        int streak = data.getDailyStreak();
        int rewardIdx = Math.min((streak - 1) % DAILY_REWARDS.length, DAILY_REWARDS.length - 1);
        long reward = DAILY_REWARDS[rewardIdx];

        // Tag-7-Bonus: Upgrade-Token
        boolean tokenBonus = (streak % 7 == 0);

        data.addMoney(reward);
        data.setLastDaily(nowTs);

        if (tokenBonus) {
            data.addUpgradeTokens(1);
            // Talent-Punkt alle 7 Tage
            data.addTalentPoints(1);
        }

        // Achievement-Tracking
        plugin.getAchievementManager().track(data, "afk_box_10", 0); // Nicht direkt relevant

        // Nachricht
        String nextInfo;
        if (streak % 7 == 0) {
            nextInfo = "<aqua>Morgen: Tag-1-Reset mit neuem Bonus!";
        } else {
            int nextIdx = Math.min(rewardIdx + 1, DAILY_REWARDS.length - 1);
            nextInfo = "<gray>Morgen: <yellow>+" + MoneyManager.formatMoney(DAILY_REWARDS[nextIdx])
                    + " <gray>(Tag " + (streak + 1) + ")";
        }

        player.sendMessage(MM.deserialize(
                "<gold>━━ 🌟 Täglicher Bonus ━━\n"
                + "<yellow>Tag " + streak + " in Folge!\n"
                + "<gray>Belohnung: <green>+$" + MoneyManager.formatMoney(reward) + "\n"
                + (tokenBonus ? "<aqua>+1 Upgrade-Token & +1 Talent-Punkt! 🎉\n" : "")
                + nextInfo));

        // Asynchron speichern
        plugin.getRepository().savePlayer(data);
    }

    public static long getRewardForDay(int day) {
        int idx = Math.min((day - 1) % DAILY_REWARDS.length, DAILY_REWARDS.length - 1);
        return DAILY_REWARDS[idx];
    }
}
