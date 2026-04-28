package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.PlayerQuest;
import de.pinkhorizon.skyblock.database.GeneratorRepository;
import de.pinkhorizon.skyblock.database.QuestRepository;
import de.pinkhorizon.skyblock.enums.QuestType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet die täglichen Quests der Spieler.
 * Jeder Spieler bekommt täglich 3 Quests (eine je Schwierigkeit: Leicht, Normal, Schwer oder zufällig).
 */
public class QuestManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Random RNG = new Random();

    private final PHSkyBlock plugin;
    private final QuestRepository questRepo;
    private final GeneratorRepository genRepo;

    /** UUID → aktive Quests des heutigen Tages */
    private final Map<UUID, List<PlayerQuest>> cache = new ConcurrentHashMap<>();

    public QuestManager(PHSkyBlock plugin, QuestRepository questRepo, GeneratorRepository genRepo) {
        this.plugin = plugin;
        this.questRepo = questRepo;
        this.genRepo = genRepo;
    }

    // ── Laden / Entladen ──────────────────────────────────────────────────────

    /**
     * Lädt oder generiert die heutigen Quests für den Spieler.
     */
    public void loadPlayer(UUID uuid) {
        List<PlayerQuest> quests = questRepo.loadTodayQuests(uuid);
        if (quests.isEmpty()) {
            quests = generateAndSaveQuests(uuid);
        }
        cache.put(uuid, quests);
    }

    public void unloadPlayer(UUID uuid) {
        List<PlayerQuest> quests = cache.remove(uuid);
        if (quests != null) {
            for (PlayerQuest q : quests) questRepo.saveQuest(uuid, q);
        }
    }

    public void savePlayer(UUID uuid) {
        List<PlayerQuest> quests = cache.get(uuid);
        if (quests != null) {
            for (PlayerQuest q : quests) questRepo.saveQuest(uuid, q);
        }
    }

    public List<PlayerQuest> getQuests(UUID uuid) {
        return cache.getOrDefault(uuid, Collections.emptyList());
    }

    // ── Quest-Generierung ─────────────────────────────────────────────────────

    private List<PlayerQuest> generateAndSaveQuests(UUID uuid) {
        List<PlayerQuest> quests = new ArrayList<>();

        // 3 verschiedene zufällige QuestTypes
        List<QuestType> types = new ArrayList<>(Arrays.asList(QuestType.values()));
        Collections.shuffle(types, RNG);

        for (int i = 0; i < 3 && i < types.size(); i++) {
            QuestType type = types.get(i);
            int diff = i; // Quest 0=Leicht, 1=Normal, 2=Schwer
            String questId = type.getId() + "_" + diff;

            PlayerQuest q = new PlayerQuest(questId, type, diff, 0, false, false, LocalDate.now());
            questRepo.insertQuest(uuid, q);
            quests.add(q);
        }

        return quests;
    }

    // ── Fortschritts-Updates ──────────────────────────────────────────────────

    public void onBlockMine(UUID uuid, Material material) {
        forEachActiveQuest(uuid, q -> {
            switch (q.getType()) {
                case MINE_COBBLESTONE -> { if (material == Material.COBBLESTONE || material == Material.COBBLED_DEEPSLATE) q.addProgress(1); }
                case MINE_IRON        -> { if (material == Material.IRON_ORE || material == Material.DEEPSLATE_IRON_ORE) q.addProgress(1); }
                case MINE_GOLD        -> { if (material == Material.GOLD_ORE || material == Material.DEEPSLATE_GOLD_ORE || material == Material.NETHER_GOLD_ORE) q.addProgress(1); }
                case MINE_DIAMOND     -> { if (material == Material.DIAMOND_ORE || material == Material.DEEPSLATE_DIAMOND_ORE) q.addProgress(1); }
                case MINE_EMERALD     -> { if (material == Material.EMERALD_ORE || material == Material.DEEPSLATE_EMERALD_ORE) q.addProgress(1); }
                case MINE_ANCIENT     -> { if (material == Material.ANCIENT_DEBRIS) q.addProgress(1); }
                default -> { /* kein Match */ }
            }
        });
        checkCompletions(uuid);
    }

    public void onBlockPlace(UUID uuid) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.PLACE_BLOCKS) q.addProgress(1);
        });
        checkCompletions(uuid);
    }

    public void onGeneratorUpgrade(UUID uuid) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.UPGRADE_GEN) q.addProgress(1);
        });
        checkCompletions(uuid);
    }

    public void onCoinsEarned(UUID uuid, long amount) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.EARN_COINS) q.addProgress(amount);
        });
        checkCompletions(uuid);
    }

    public void onGeneratorCollect(UUID uuid) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.COLLECT_GEN) q.addProgress(1);
        });
        checkCompletions(uuid);
    }

    public void onIslandScoreUpdate(UUID uuid, long score) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.ISLAND_SCORE && score >= q.getGoal()) {
                q.addProgress(q.getGoal()); // direkter Abschluss
            }
        });
        checkCompletions(uuid);
    }

    public void onIslandVisit(UUID uuid) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.VISIT_ISLANDS) q.addProgress(1);
        });
        checkCompletions(uuid);
    }

    public void onCropHarvest(UUID uuid, int amount) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.HARVEST_CROPS) q.addProgress(amount);
        });
        checkCompletions(uuid);
    }

    public void onFishCatch(UUID uuid) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.CATCH_FISH) q.addProgress(1);
        });
        checkCompletions(uuid);
    }

    public void onAnimalBreed(UUID uuid) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.BREED_ANIMALS) q.addProgress(1);
        });
        checkCompletions(uuid);
    }

    public void onShopSell(UUID uuid, long coins) {
        forEachActiveQuest(uuid, q -> {
            if (q.getType() == QuestType.SELL_ITEMS) q.addProgress(coins);
        });
        checkCompletions(uuid);
    }

    // ── Belohnung einlösen ────────────────────────────────────────────────────

    public boolean claimReward(Player player, PlayerQuest quest) {
        if (!quest.isCompleted() || quest.isRewardClaimed()) return false;

        quest.setRewardClaimed(true);
        questRepo.saveQuest(player.getUniqueId(), quest);

        // Coins-Belohnung
        plugin.getCoinManager().addCoins(player.getUniqueId(), quest.getReward());

        // total_quests_done erhöhen
        genRepo.incrementQuestsDone(player.getUniqueId());

        // Achievement-Check
        plugin.getAchievementManager().checkQuestAchievements(player);

        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<green>Quest abgeschlossen! <gold>+"
            + String.format("%,d", quest.getReward()) + " Coins"));
        player.playSound(player.getLocation(),
            org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        return true;
    }

    // ── Abschluss-Benachrichtigung ────────────────────────────────────────────

    private void checkCompletions(UUID uuid) {
        List<PlayerQuest> quests = cache.get(uuid);
        if (quests == null) return;
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) return;

        for (PlayerQuest q : quests) {
            if (q.isCompleted() && !q.isRewardClaimed() && !q.isNotified()) {
                q.setNotified(true);
                String questName = q.getType().getName().replaceAll("§[0-9a-fA-Fk-oK-OrR]", "");
                player.sendMessage(MM.deserialize(
                    "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                    + "<yellow>Quest abschließbar: <white>" + questName
                    + " <gray>– <green>/is quest"));
            }
        }
    }

    private void forEachActiveQuest(UUID uuid, java.util.function.Consumer<PlayerQuest> action) {
        List<PlayerQuest> quests = cache.get(uuid);
        if (quests == null) return;
        for (PlayerQuest q : quests) {
            if (!q.isCompleted()) action.accept(q);
        }
    }
}
