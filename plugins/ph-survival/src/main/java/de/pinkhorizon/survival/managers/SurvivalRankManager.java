package de.pinkhorizon.survival.managers;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.core.database.RankRepository;
import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SurvivalRankManager {

    public static class Rank {
        public final String id;
        public final String chatPrefix;
        public final TextColor nameColor;
        public final int maxClaims;
        public final int maxHomes;
        public final int priority;

        public Rank(String id, String chatPrefix, TextColor nameColor, int maxClaims, int maxHomes, int priority) {
            this.id = id;
            this.chatPrefix = chatPrefix;
            this.nameColor  = nameColor;
            this.maxClaims  = maxClaims;
            this.maxHomes   = maxHomes;
            this.priority   = priority;
        }
    }

    public static final LinkedHashMap<String, Rank> RANKS = new LinkedHashMap<>();

    static {
        RANKS.put("owner",     new Rank("owner",     "\u00a74\u00a7l[Owner] ",   TextColor.color(0xCC0000),  9999, 20, 1));
        RANKS.put("admin",     new Rank("admin",     "\u00a7c\u00a7l[Admin] ",   NamedTextColor.RED,         9999, 15, 2));
        RANKS.put("dev",       new Rank("dev",       "\u00a7b\u00a7l[DEV] ",     TextColor.color(0x00CCCC),  9999, 15, 3));
        RANKS.put("moderator", new Rank("moderator", "\u00a79\u00a7l[Mod] ",     NamedTextColor.BLUE,         100,  8, 4));
        RANKS.put("supporter", new Rank("supporter", "\u00a73\u00a7l[Support] ", TextColor.color(0x00AAAA),   50,   6, 5));
        RANKS.put("vip",       new Rank("vip",       "\u00a76[VIP] ",            NamedTextColor.GOLD,          15,  3, 6));
        RANKS.put("spieler",   new Rank("spieler",   "",                         NamedTextColor.WHITE,         10,  2, 7));
    }

    private final PHSurvival plugin;
    private final RankRepository repo;
    // In-Memory-Cache: UUID → rankId
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();

    public SurvivalRankManager(PHSurvival plugin) {
        this.plugin = plugin;
        this.repo   = PHCore.getInstance().getRankRepository();
        // Alle Ränge beim Start aus DB laden
        cache.putAll(repo.loadAll());
        plugin.getLogger().info("SurvivalRankManager: " + cache.size() + " Rang-Einträge aus DB geladen.");
    }

    public Rank getRank(UUID uuid) {
        String rankId = cache.computeIfAbsent(uuid, repo::getRank);
        return RANKS.getOrDefault(rankId, RANKS.get("spieler"));
    }

    public void setRank(UUID uuid, String playerName, String rankId) {
        if (!RANKS.containsKey(rankId)) return;
        cache.put(uuid, rankId);
        repo.setRank(uuid, playerName, rankId);
    }

    public int getMaxClaims(UUID uuid) {
        return getRank(uuid).maxClaims + plugin.getUpgradeManager().getExtraClaims(uuid);
    }

    public int getMaxHomes(UUID uuid) {
        return getRank(uuid).maxHomes + plugin.getUpgradeManager().getExtraHomes(uuid);
    }

    public void applyTabName(Player player) {
        Rank rank = getRank(player.getUniqueId());
        player.playerListName(net.kyori.adventure.text.Component.text(rank.chatPrefix + player.getName()));
        plugin.getScoreboardManager().updateTabSort(player);
    }
}
