package de.pinkhorizon.survival.managers;

import de.pinkhorizon.core.integration.LuckPermsHook;
import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
            this.nameColor = nameColor;
            this.maxClaims = maxClaims;
            this.maxHomes = maxHomes;
            this.priority = priority;
        }
    }

    public static final LinkedHashMap<String, Rank> RANKS = new LinkedHashMap<>();

    static {
        RANKS.put("owner",   new Rank("owner",   "\u00a74\u00a7l[Owner] ", TextColor.color(0xCC0000), 9999, 20, 1));
        RANKS.put("admin",   new Rank("admin",   "\u00a7c\u00a7l[Admin] ", NamedTextColor.RED,          9999, 10, 2));
        RANKS.put("mod",     new Rank("mod",     "\u00a79\u00a7l[Mod] ",   NamedTextColor.BLUE,          100,  8, 3));
        RANKS.put("mvp",     new Rank("mvp",     "\u00a76\u00a7l[MVP] ",   NamedTextColor.GOLD,           30,  5, 4));
        RANKS.put("vip",     new Rank("vip",     "\u00a7a[VIP] ",          NamedTextColor.GREEN,          15,  3, 5));
        RANKS.put("spieler", new Rank("spieler", "",                       NamedTextColor.WHITE,          10,  2, 6));
    }

    private final PHSurvival plugin;
    private File dataFile;
    private YamlConfiguration data;
    // UUID -> rankId
    private final Map<UUID, String> playerRanks = new LinkedHashMap<>();

    public SurvivalRankManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    public Rank getRank(UUID uuid) {
        String rankId = playerRanks.getOrDefault(uuid, "spieler");
        return RANKS.getOrDefault(rankId, RANKS.get("spieler"));
    }

    public void setRank(UUID uuid, String playerName, String rankId) {
        if (!RANKS.containsKey(rankId)) return;
        playerRanks.put(uuid, rankId);
        data.set("ranks." + uuid + ".rank", rankId);
        data.set("ranks." + uuid + ".name", playerName);
        saveToDisk();
        LuckPermsHook.setGroup(uuid, rankId);
    }

    public int getMaxClaims(UUID uuid) {
        return getRank(uuid).maxClaims + plugin.getUpgradeManager().getExtraClaims(uuid);
    }

    public int getMaxHomes(UUID uuid) {
        return getRank(uuid).maxHomes;
    }

    public void applyTabName(Player player) {
        Rank rank = getRank(player.getUniqueId());
        player.playerListName(net.kyori.adventure.text.Component.text(rank.chatPrefix + player.getName()));
    }

    private void saveToDisk() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Ranks konnten nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "ranks.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.contains("ranks")) return;
        for (String uuidStr : data.getConfigurationSection("ranks").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String rankId = data.getString("ranks." + uuidStr + ".rank", "spieler");
                playerRanks.put(uuid, rankId);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
