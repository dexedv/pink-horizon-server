package de.pinkhorizon.survival.managers;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.core.database.RankRepository;
import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
        public final int maxAuctions;
        public final int priority;

        public Rank(String id, String chatPrefix, TextColor nameColor, int maxClaims, int maxHomes, int maxAuctions, int priority) {
            this.id          = id;
            this.chatPrefix  = chatPrefix;
            this.nameColor   = nameColor;
            this.maxClaims   = maxClaims;
            this.maxHomes    = maxHomes;
            this.maxAuctions = maxAuctions;
            this.priority    = priority;
        }
    }

    public static final LinkedHashMap<String, Rank> RANKS = new LinkedHashMap<>();

    static {
        //                                                                              Claims Homes Aukt Prio
        RANKS.put("owner",     new Rank("owner",     "\u00a74\u00a7l[Owner] ",   TextColor.color(0xCC0000),  9999, 20, 99, 1));
        RANKS.put("admin",     new Rank("admin",     "\u00a7c\u00a7l[Admin] ",   NamedTextColor.RED,         9999, 15, 99, 2));
        RANKS.put("dev",       new Rank("dev",       "\u00a7b\u00a7l[DEV] ",     TextColor.color(0x00CCCC),  9999, 15, 99, 3));
        RANKS.put("moderator", new Rank("moderator", "\u00a79\u00a7l[Mod] ",     NamedTextColor.BLUE,         100,  8, 20, 4));
        RANKS.put("supporter", new Rank("supporter", "\u00a73\u00a7l[Support] ", TextColor.color(0x00AAAA),    50,  6, 15, 5));
        RANKS.put("vip",       new Rank("vip",       "\u00a76[VIP] ",            NamedTextColor.GOLD,           20,  3, 10, 6));
        // Shop-Ränge
        RANKS.put("legende",   new Rank("legende",   "\u00a7d[\u00a75\u2756 Legende\u00a7d] ", TextColor.color(0xFF69B4), 40, 4, 15, 7));
        RANKS.put("krieger",   new Rank("krieger",   "\u00a76[Krieger] ",         NamedTextColor.GOLD,           30,  3, 10, 8));
        RANKS.put("siedler",   new Rank("siedler",   "\u00a7a[Siedler] ",         NamedTextColor.GREEN,          20,  2,  7, 9));
        RANKS.put("spieler",   new Rank("spieler",   "",                         NamedTextColor.WHITE,           10,  2,  5, 10));
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

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public void applyTabName(Player player) {
        Rank rank   = getRank(player.getUniqueId());
        boolean afk = plugin.getAfkManager().isAfk(player.getUniqueId());

        Component badge = rank.chatPrefix.isBlank()
            ? Component.empty()
            : LEGACY.deserialize(rank.chatPrefix.trim())
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" "));

        Component afkTag = afk
            ? Component.text("[AFK] ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            : Component.empty();

        Component name = Component.text(player.getName(), rank.nameColor)
            .decoration(TextDecoration.ITALIC, false);

        player.playerListName(badge.append(afkTag).append(name));
        plugin.getScoreboardManager().updateTabSort(player);
    }
}
