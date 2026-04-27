package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.database.GeneratorRepository;
import de.pinkhorizon.skyblock.enums.AchievementType;
import de.pinkhorizon.skyblock.enums.TitleType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Prüft und vergibt Achievements.
 * Cache: UUID → Set<achievementId>
 */
public class AchievementManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;
    private final GeneratorRepository repo;

    /** In-memory Cache der freigeschalteten Achievements. */
    private final Map<UUID, Set<String>> cache = new HashMap<>();

    public AchievementManager(PHSkyBlock plugin, GeneratorRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    public void loadPlayer(UUID uuid) {
        cache.put(uuid, new HashSet<>(repo.loadAchievements(uuid)));
    }

    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    public boolean hasAchievement(UUID uuid, String id) {
        Set<String> set = cache.get(uuid);
        return set != null && set.contains(id);
    }

    public Set<String> getUnlocked(UUID uuid) {
        return cache.getOrDefault(uuid, Set.of());
    }

    // ── Vergabe ───────────────────────────────────────────────────────────────

    /**
     * Prüft ob das Achievement noch nicht vergeben ist, vergibt es und sendet
     * eine Benachrichtigung.
     */
    public void checkAndAward(Player player, AchievementType type) {
        UUID uuid = player.getUniqueId();
        Set<String> unlocked = cache.computeIfAbsent(uuid, k -> new HashSet<>());

        if (unlocked.contains(type.getId())) return; // bereits freigeschaltet

        // In DB + Cache speichern
        repo.unlockAchievement(uuid, type.getId());
        unlocked.add(type.getId());

        // Coin-Belohnung
        if (type.getCoinReward() > 0) {
            repo.addCoins(uuid, type.getCoinReward());
        }

        // Titel-Belohnung: als "title_<id>" in der Achievement-Tabelle speichern
        if (type.getTitleReward() != null) {
            String titleKey = "title_" + type.getTitleReward().getId();
            if (!unlocked.contains(titleKey)) {
                repo.unlockAchievement(uuid, titleKey);
                unlocked.add(titleKey);
            }
        }

        // Benachrichtigung
        sendUnlockMessage(player, type);
    }

    /** Direkt einen Titel als freigeschaltet markieren (z.B. beim Kauf). */
    public void grantTitleOwnership(UUID uuid, TitleType title) {
        String key = "title_" + title.getId();
        Set<String> unlocked = cache.computeIfAbsent(uuid, k -> new HashSet<>());
        if (!unlocked.contains(key)) {
            repo.unlockAchievement(uuid, key);
            unlocked.add(key);
        }
    }

    /** Prüft ob der Spieler einen bestimmten Titel besitzt. */
    public boolean ownsTitle(UUID uuid, TitleType title) {
        if (title == TitleType.KEIN_TITEL) return true;
        return hasAchievement(uuid, "title_" + title.getId());
    }

    // ── Check-Methoden (aufgerufen von anderen Managern) ─────────────────────

    public void checkGeneratorLevelAchievements(Player player, int level) {
        if (level >= 5)  checkAndAward(player, AchievementType.GEN_LVL_5);
        if (level >= 10) checkAndAward(player, AchievementType.GEN_LVL_10);
        if (level >= 20) checkAndAward(player, AchievementType.GEN_LVL_20);
        if (level >= 35) checkAndAward(player, AchievementType.GEN_LVL_35);
        if (level >= 50) checkAndAward(player, AchievementType.GEN_LVL_50);
    }

    public void checkMiningAchievements(Player player) {
        long mined = repo.getTotalMined(player.getUniqueId());
        if (mined >= 100)     checkAndAward(player, AchievementType.MINE_100);
        if (mined >= 1000)    checkAndAward(player, AchievementType.MINE_1K);
        if (mined >= 10000)   checkAndAward(player, AchievementType.MINE_10K);
        if (mined >= 100000)  checkAndAward(player, AchievementType.MINE_100K);
        if (mined >= 1000000) checkAndAward(player, AchievementType.MINE_1M);
    }

    public void checkCoinAchievements(Player player) {
        long coins = repo.getCoins(player.getUniqueId());
        if (coins >= 1000)    checkAndAward(player, AchievementType.COINS_1K);
        if (coins >= 10000)   checkAndAward(player, AchievementType.COINS_10K);
        if (coins >= 100000)  checkAndAward(player, AchievementType.COINS_100K);
        if (coins >= 1000000) checkAndAward(player, AchievementType.COINS_1M);
    }

    public void checkScoreAchievements(Player player, long score) {
        if (score >= 100)    checkAndAward(player, AchievementType.SCORE_100);
        if (score >= 1000)   checkAndAward(player, AchievementType.SCORE_1K);
        if (score >= 10000)  checkAndAward(player, AchievementType.SCORE_10K);
        if (score >= 100000) checkAndAward(player, AchievementType.SCORE_100K);
    }

    public void checkQuestAchievements(Player player) {
        int done = repo.getTotalQuestsDone(player.getUniqueId());
        if (done >= 10)  checkAndAward(player, AchievementType.QUEST_10);
        if (done >= 50)  checkAndAward(player, AchievementType.QUEST_50);
        if (done >= 200) checkAndAward(player, AchievementType.QUEST_200);
    }

    public void checkLoginStreakAchievements(Player player, int streak) {
        if (streak >= 7)  checkAndAward(player, AchievementType.LOGIN_STREAK_7);
        if (streak >= 30) checkAndAward(player, AchievementType.LOGIN_STREAK_30);
    }

    public void onFirstIsland(Player player)  { checkAndAward(player, AchievementType.FIRST_ISLAND); }
    public void onFirstGenerator(Player player){ checkAndAward(player, AchievementType.FIRST_GEN); }
    public void onFirstAutoSell(Player player) { checkAndAward(player, AchievementType.FIRST_SELL); }

    public void checkInviteAchievements(Player player, int totalInvites) {
        if (totalInvites >= 1)  checkAndAward(player, AchievementType.INVITE_1);
        if (totalInvites >= 5)  checkAndAward(player, AchievementType.INVITE_5);
        if (totalInvites >= 10) checkAndAward(player, AchievementType.INVITE_10);
    }

    public void checkVisitAchievements(Player player, int totalVisits) {
        if (totalVisits >= 5)   checkAndAward(player, AchievementType.VISIT_5);
        if (totalVisits >= 25)  checkAndAward(player, AchievementType.VISIT_25);
        if (totalVisits >= 100) checkAndAward(player, AchievementType.VISIT_100);
    }

    // ── Benachrichtigung ──────────────────────────────────────────────────────

    private void sendUnlockMessage(Player player, AchievementType type) {
        player.sendMessage(MM.deserialize(""));
        player.sendMessage(MM.deserialize(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(MM.deserialize(
            "   <gold>★ <yellow><bold>Achievement freigeschaltet!</bold> <gold>★"));
        player.sendMessage(MM.deserialize(
            "   <gray>» " + type.getName()));
        player.sendMessage(MM.deserialize(
            "   <dark_gray>" + type.getDescription().replace("§", "§")));
        if (type.getCoinReward() > 0) {
            player.sendMessage(MM.deserialize(
                "   <yellow>+<gold><bold>" + String.format("%,d", type.getCoinReward())
                + " Coins</bold> <yellow>Belohnung!"));
        }
        if (type.getTitleReward() != null) {
            player.sendMessage(MM.deserialize(
                "   <light_purple>Titel freigeschaltet: "
                + type.getTitleReward().getCleanChatPrefix()));
        }
        player.sendMessage(MM.deserialize(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(MM.deserialize(""));

        // Sound
        player.playSound(player.getLocation(),
            org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }
}
