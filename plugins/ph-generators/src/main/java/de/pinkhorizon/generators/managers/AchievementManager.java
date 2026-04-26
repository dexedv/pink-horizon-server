package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Verwaltet das Achievement-System.
 * Achievement-Definitionen kommen aus der config.yml.
 */
public class AchievementManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** UUID → Achievement-ID → Fortschritt */
    private final Map<UUID, Map<String, Long>> progressCache = new HashMap<>();
    /** UUID → Set von abgeschlossenen Achievement-IDs */
    private final Map<UUID, Set<String>> completedCache = new HashMap<>();

    public AchievementManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void loadPlayer(PlayerData data) {
        progressCache.put(data.getUuid(), new HashMap<>());
        completedCache.put(data.getUuid(), new HashSet<>());

        // Aus DB laden
        try {
            java.sql.ResultSet rs = plugin.getRepository().getAllAchievements(data.getUuid());
            while (rs.next()) {
                String id = rs.getString("achievement_id");
                long prog = rs.getLong("progress");
                boolean done = rs.getInt("completed") == 1;
                progressCache.get(data.getUuid()).put(id, prog);
                if (done) completedCache.get(data.getUuid()).add(id);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[AchievementManager] loadPlayer: " + e.getMessage());
        }
    }

    public void unloadPlayer(UUID uuid) {
        progressCache.remove(uuid);
        completedCache.remove(uuid);
    }

    /**
     * Trackt Fortschritt für ein Achievement.
     * @param data   Spielerdaten
     * @param id     Achievement-ID (aus config.yml)
     * @param amount Fortschritt-Zuwachs (meist 1, aber für Geld-Achievements die Menge)
     */
    public void track(PlayerData data, String id, long amount) {
        if (!plugin.getConfig().contains("achievements." + id)) return;

        Set<String> done = completedCache.computeIfAbsent(data.getUuid(), k -> new HashSet<>());
        if (done.contains(id)) return;

        Map<String, Long> prog = progressCache.computeIfAbsent(data.getUuid(), k -> new HashMap<>());
        long newProg = prog.getOrDefault(id, 0L) + amount;
        long target = plugin.getConfig().getLong("achievements." + id + ".target", 1L);

        // Bei Prestige und ähnlichem: direkter Vergleich (kein Addieren)
        if (id.startsWith("prestige_")) {
            newProg = amount; // amount ist der aktuelle Prestige-Wert
        }

        prog.put(id, newProg);

        if (newProg >= target) {
            done.add(id);
            rewardAchievement(data, id);
            plugin.getRepository().upsertAchievement(data.getUuid(), id, newProg, true);
        } else {
            plugin.getRepository().upsertAchievement(data.getUuid(), id, newProg, false);
        }
    }

    private void rewardAchievement(PlayerData data, String id) {
        long reward = plugin.getConfig().getLong("achievements." + id + ".reward-money", 0L);
        String name = plugin.getConfig().getString("achievements." + id + ".name", id);

        data.addMoney(reward);

        Player player = Bukkit.getPlayer(data.getUuid());
        if (player != null) {
            player.sendMessage(MM.deserialize(
                    "<gold>✦ Achievement freigeschaltet: " + name
                            + " <gray>| <green>+$" + MoneyManager.formatMoney(reward)));
        }
    }

    /** Gibt eine formatierte Achievement-Liste zurück */
    public String getAchievementList(PlayerData data) {
        Set<String> done = completedCache.getOrDefault(data.getUuid(), new HashSet<>());
        Map<String, Long> prog = progressCache.getOrDefault(data.getUuid(), new HashMap<>());

        StringBuilder sb = new StringBuilder("<gold>━━ Achievements ━━");
        int completed = 0;
        int total = 0;

        if (plugin.getConfig().contains("achievements")) {
            for (String id : plugin.getConfig().getConfigurationSection("achievements").getKeys(false)) {
                total++;
                String name = plugin.getConfig().getString("achievements." + id + ".name", id);
                String desc = plugin.getConfig().getString("achievements." + id + ".description", "");
                long target = plugin.getConfig().getLong("achievements." + id + ".target", 1L);
                long p = prog.getOrDefault(id, 0L);
                boolean isDone = done.contains(id);
                if (isDone) completed++;

                String status = isDone ? "<green>✔" : "<red>✗";
                sb.append("\n ").append(status).append(" <white>").append(name);
                if (!isDone) {
                    sb.append(" <gray>[").append(p).append("/").append(target).append("]");
                }
            }
        }

        sb.insert(sb.indexOf("━━") + 2, " <gray>(" + completed + "/" + total + ")<gold>");
        return sb.toString();
    }

    public int getCompletedCount(UUID uuid) {
        return completedCache.getOrDefault(uuid, new HashSet<>()).size();
    }
}
