package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class QuestManager {

    public enum QuestType {
        MINE_ORES    ("50 Erze abbauen",      50),
        CUT_TREES    ("30 Bäume fällen",       30),
        KILL_MOBS    ("25 Monster töten",      25),
        CATCH_FISH   ("20 Fische angeln",      20),
        HARVEST_CROPS("40 Pflanzen ernten",    40);

        public final String description;
        public final int goal;

        QuestType(String description, int goal) {
            this.description = description;
            this.goal = goal;
        }
    }

    public record Quest(QuestType type, int progress, boolean completed) {}

    private static final long QUEST_REWARD = 300L;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PHSurvival plugin;
    private File dataFile;
    private YamlConfiguration data;

    public QuestManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    /** Returns today's 3 quests for the player, resetting if the day changed. */
    public List<Quest> getDailyQuests(UUID uuid) {
        String base = "quests." + uuid;
        String today = LocalDate.now().format(DATE_FMT);
        if (!today.equals(data.getString(base + ".date", ""))) {
            resetQuests(uuid, today);
        }
        List<Quest> result = new ArrayList<>();
        for (String typeStr : data.getStringList(base + ".types")) {
            try {
                QuestType type = QuestType.valueOf(typeStr);
                int progress  = data.getInt(base + ".progress." + typeStr, 0);
                boolean done  = data.getBoolean(base + ".completed." + typeStr, false);
                result.add(new Quest(type, progress, done));
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    /** Called from listeners to add quest progress. */
    public void addProgress(Player player, QuestType type, int amount) {
        // Ensure quests are initialized / reset
        getDailyQuests(player.getUniqueId());

        String base = "quests." + player.getUniqueId();
        if (!data.getStringList(base + ".types").contains(type.name())) return;
        if (data.getBoolean(base + ".completed." + type.name(), false)) return;

        int current    = data.getInt(base + ".progress." + type.name(), 0);
        int newProgress = Math.min(current + amount, type.goal);
        data.set(base + ".progress." + type.name(), newProgress);

        if (newProgress >= type.goal) {
            data.set(base + ".completed." + type.name(), true);
            plugin.getEconomyManager().deposit(player.getUniqueId(), QUEST_REWARD);
            player.sendMessage(Component.text(
                "§6§l✦ Quest abgeschlossen: §f" + type.description + " §8(+" + QUEST_REWARD + " Coins)"));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            plugin.getAchievementManager().unlock(player, AchievementManager.Achievement.QUEST_DONE);
            plugin.getAchievementManager().checkCoins(player);
        }
        save();
    }

    private void resetQuests(UUID uuid, String today) {
        List<QuestType> all = new ArrayList<>(Arrays.asList(QuestType.values()));
        Collections.shuffle(all);
        List<String> selected = all.subList(0, Math.min(3, all.size()))
                                   .stream().map(Enum::name).toList();
        String base = "quests." + uuid;
        data.set(base + ".date", today);
        data.set(base + ".types", selected);
        data.set(base + ".progress", null);
        data.set(base + ".completed", null);
        save();
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "quests.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try { data.save(dataFile); }
        catch (IOException ex) { plugin.getLogger().warning("Quests konnten nicht gespeichert werden: " + ex.getMessage()); }
    }
}
