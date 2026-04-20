package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.core.database.RankRepository;
import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {

    public static class Rank {
        public final String id;
        public final String chatPrefix;
        public final String tabPrefix;
        public final TextColor nameColor;
        public final int priority;

        public Rank(String id, String chatPrefix, String tabPrefix, TextColor nameColor, int priority) {
            this.id          = id;
            this.chatPrefix  = chatPrefix;
            this.tabPrefix   = tabPrefix;
            this.nameColor   = nameColor;
            this.priority    = priority;
        }
    }

    public static final LinkedHashMap<String, Rank> RANKS = new LinkedHashMap<>();

    static {
        RANKS.put("owner",     new Rank("owner",     "§4§l[Owner] §r",   "§4[Owner] ",   TextColor.color(0xCC0000),  1));
        RANKS.put("admin",     new Rank("admin",     "§c§l[Admin] §r",   "§c[Admin] ",   NamedTextColor.RED,         2));
        RANKS.put("dev",       new Rank("dev",       "§b§l[DEV] §r",     "§b[DEV] ",     TextColor.color(0x00CCCC),  3));
        RANKS.put("moderator", new Rank("moderator", "§9§l[Mod] §r",     "§9[Mod] ",     NamedTextColor.BLUE,        4));
        RANKS.put("supporter", new Rank("supporter", "§3§l[Support] §r", "§3[Support] ", TextColor.color(0x00AAAA),  5));
        RANKS.put("vip",       new Rank("vip",       "§6[VIP] §r",       "§6[VIP] ",     NamedTextColor.GOLD,        6));
        RANKS.put("spieler",   new Rank("spieler",   "",                 "",             NamedTextColor.WHITE,       7));
    }

    private final PHLobby plugin;
    private final RankRepository repo;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();

    public RankManager(PHLobby plugin) {
        this.plugin = plugin;
        this.repo   = PHCore.getInstance().getRankRepository();
        cache.putAll(repo.loadAll());
        plugin.getLogger().info("RankManager: " + cache.size() + " Rang-Einträge aus DB geladen.");
    }

    public Rank getRank(UUID uuid) {
        String rankId = cache.computeIfAbsent(uuid, repo::getRank);
        return RANKS.getOrDefault(rankId, RANKS.get("spieler"));
    }

    public String getRankId(UUID uuid) {
        return cache.computeIfAbsent(uuid, repo::getRank);
    }

    public void setRank(UUID uuid, String playerName, String rankId) {
        if (!RANKS.containsKey(rankId)) return;
        cache.put(uuid, rankId);
        repo.setRank(uuid, playerName, rankId);
    }

    public void applyTabName(Player player) {
        Rank rank = getRank(player.getUniqueId());
        player.playerListName(Component.text(rank.tabPrefix + player.getName()));
    }
}
