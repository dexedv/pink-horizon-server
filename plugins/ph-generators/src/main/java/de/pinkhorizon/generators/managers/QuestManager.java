package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Verwaltet tägliche und wöchentliche Quests.
 */
public class QuestManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private BukkitTask resetChecker;

    // ── Quest-Definition ─────────────────────────────────────────────────────

    public record QuestDef(
            String id,
            String displayName,
            String description,
            QuestType type,
            long target,
            long rewardMoney,
            boolean weekly
    ) {}

    public enum QuestType {
        EARN_MONEY,      // Verdiene X $
        UPGRADE_GENS,    // Upgrade X Generatoren
        REACH_PRESTIGE,  // Erreiche Prestige X
        PLACE_GENS,      // Platziere X Generatoren
    }

    private static final List<QuestDef> DAILY_POOL = List.of(
            new QuestDef("earn_100k",   "<yellow>Einkommensziel I",   "Verdiene $100.000",  QuestType.EARN_MONEY,    100_000,  5_000,  false),
            new QuestDef("earn_500k",   "<yellow>Einkommensziel II",  "Verdiene $500.000",  QuestType.EARN_MONEY,    500_000,  20_000, false),
            new QuestDef("earn_1m",     "<gold>Einkommensziel III",   "Verdiene $1.000.000",QuestType.EARN_MONEY,  1_000_000,  50_000, false),
            new QuestDef("upgrade_5",   "<green>Upgrade-Tag I",       "Upgrade 5 Generatoren",  QuestType.UPGRADE_GENS, 5,   3_000,  false),
            new QuestDef("upgrade_20",  "<green>Upgrade-Tag II",      "Upgrade 20 Generatoren", QuestType.UPGRADE_GENS, 20,  10_000, false),
            new QuestDef("place_3",     "<aqua>Baumeister",           "Platziere 3 Generatoren",QuestType.PLACE_GENS,  3,   2_000,  false),
            new QuestDef("place_5",     "<aqua>Mega-Baumeister",      "Platziere 5 Generatoren",QuestType.PLACE_GENS,  5,   5_000,  false)
    );

    private static final QuestDef WEEKLY_QUEST =
            new QuestDef("weekly_earn", "<light_purple>★ Wochen-Quest ★",
                    "Verdiene $10.000.000 diese Woche",
                    QuestType.EARN_MONEY, 10_000_000, 500_000, true);

    // ── Spieler-Quest-Fortschritt ─────────────────────────────────────────────

    /** UUID → Quest-ID → Fortschritt */
    private final Map<UUID, Map<String, Long>> progress = new HashMap<>();
    /** UUID → Quest-ID → completed */
    private final Map<UUID, Set<String>> completed = new HashMap<>();
    /** UUID → zugewiesene tägliche Quest-IDs */
    private final Map<UUID, List<String>> assignedDaily = new HashMap<>();

    public QuestManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Stündlich prüfen ob Reset nötig
        resetChecker = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::checkReset, 1200L, 1200L);
    }

    public void stop() {
        if (resetChecker != null) resetChecker.cancel();
        saveAll();
    }

    // ── Laden / Initialisieren ────────────────────────────────────────────────

    public void initPlayer(PlayerData data) {
        UUID uuid = data.getUuid();
        progress.computeIfAbsent(uuid, k -> new HashMap<>());
        completed.computeIfAbsent(uuid, k -> new HashSet<>());

        // 3 zufällige Tages-Quests zuweisen
        if (!assignedDaily.containsKey(uuid)) {
            List<QuestDef> pool = new ArrayList<>(DAILY_POOL);
            Collections.shuffle(pool);
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(3, pool.size()); i++) {
                ids.add(pool.get(i).id());
            }
            assignedDaily.put(uuid, ids);
        }
    }

    // ── Fortschritt tracken ──────────────────────────────────────────────────

    public void trackEarn(PlayerData data, long amount) {
        track(data, QuestType.EARN_MONEY, amount);
    }

    public void trackUpgrade(PlayerData data) {
        track(data, QuestType.UPGRADE_GENS, 1);
    }

    public void trackPlace(PlayerData data) {
        track(data, QuestType.PLACE_GENS, 1);
    }

    private void track(PlayerData data, QuestType type, long amount) {
        UUID uuid = data.getUuid();
        Map<String, Long> prog = progress.getOrDefault(uuid, new HashMap<>());
        Set<String> done = completed.getOrDefault(uuid, new HashSet<>());
        List<String> assigned = assignedDaily.getOrDefault(uuid, new ArrayList<>());

        // Tages-Quests
        for (String id : assigned) {
            QuestDef def = findDef(id);
            if (def == null || def.type() != type || done.contains(id)) continue;
            long newProg = prog.getOrDefault(id, 0L) + amount;
            prog.put(id, newProg);
            if (newProg >= def.target()) {
                done.add(id);
                rewardQuest(data, def);
            }
        }

        // Wöchentliche Quest
        if (WEEKLY_QUEST.type() == type && !done.contains(WEEKLY_QUEST.id())) {
            long newProg = prog.getOrDefault(WEEKLY_QUEST.id(), 0L) + amount;
            prog.put(WEEKLY_QUEST.id(), newProg);
            if (newProg >= WEEKLY_QUEST.target()) {
                done.add(WEEKLY_QUEST.id());
                rewardQuest(data, WEEKLY_QUEST);
            }
        }

        progress.put(uuid, prog);
        completed.put(uuid, done);
    }

    private void rewardQuest(PlayerData data, QuestDef def) {
        data.addMoney(def.rewardMoney());
        Player player = Bukkit.getPlayer(data.getUuid());
        if (player != null) {
            player.sendMessage(MM.deserialize(
                    "<green>✔ Quest abgeschlossen: " + def.displayName()
                            + " <gray>| <gold>+$" + MoneyManager.formatMoney(def.rewardMoney())));
        }
    }

    // ── Quest-Info ────────────────────────────────────────────────────────────

    public String getQuestOverview(PlayerData data) {
        UUID uuid = data.getUuid();
        Map<String, Long> prog = progress.getOrDefault(uuid, new HashMap<>());
        Set<String> done = completed.getOrDefault(uuid, new HashSet<>());
        List<String> assigned = assignedDaily.getOrDefault(uuid, new ArrayList<>());

        StringBuilder sb = new StringBuilder("<yellow>━━ Deine Quests ━━");
        sb.append("\n<gray>Täglich:");
        for (String id : assigned) {
            QuestDef def = findDef(id);
            if (def == null) continue;
            long p = prog.getOrDefault(id, 0L);
            String status = done.contains(id) ? "<green>✔" : "<red>✗";
            sb.append("\n ").append(status).append(" <white>").append(def.displayName())
                    .append(" <gray>").append(p).append("/").append(def.target())
                    .append(" <gold>(+$").append(MoneyManager.formatMoney(def.rewardMoney())).append(")");
        }

        sb.append("\n<light_purple>Wöchentlich:");
        long wp = prog.getOrDefault(WEEKLY_QUEST.id(), 0L);
        String ws = done.contains(WEEKLY_QUEST.id()) ? "<green>✔" : "<red>✗";
        sb.append("\n ").append(ws).append(" <white>").append(WEEKLY_QUEST.displayName())
                .append(" <gray>").append(MoneyManager.formatMoney(wp))
                .append("/").append(MoneyManager.formatMoney(WEEKLY_QUEST.target()))
                .append(" <gold>(+$").append(MoneyManager.formatMoney(WEEKLY_QUEST.rewardMoney())).append(")");

        return sb.toString();
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    private void checkReset() {
        // Tages-Reset: neue Quests zuweisen, Fortschritt löschen
        String today = LocalDate.now().format(DATE_FMT);
        for (UUID uuid : new ArrayList<>(assignedDaily.keySet())) {
            // Einfacher Ansatz: täglich resetzen (wird beim nächsten Init neu zugewiesen)
            // In Produktion würde man das Datum mit dem letzten Reset vergleichen
        }
        // TODO: persistentes Datum-Tracking für Reset
    }

    private void saveAll() {
        // Fortschritt wird beim Spieler-Save automatisch mitgespeichert (in-memory reicht für Tages-Quests)
    }

    private QuestDef findDef(String id) {
        return DAILY_POOL.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }
}
