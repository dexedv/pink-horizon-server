package de.pinkhorizon.lobby.managers;

import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RankManager {

    private final PHLobby plugin;
    private final Map<UUID, String> playerRanks = new HashMap<>();
    private final File dataFile;
    private final YamlConfiguration data;

    // ── Rang-Definitionen ────────────────────────────────────────────────
    public static class Rank {
        public final String id;
        public final String chatPrefix;   // §-Codes für Chat
        public final String tabPrefix;    // §-Codes für Tab-Liste
        public final TextColor nameColor; // Farbe des Namens im Chat
        public final int priority;        // Kleinere Zahl = höherer Rang

        public Rank(String id, String chatPrefix, String tabPrefix, TextColor nameColor, int priority) {
            this.id = id;
            this.chatPrefix  = chatPrefix;
            this.tabPrefix   = tabPrefix;
            this.nameColor   = nameColor;
            this.priority    = priority;
        }
    }

    // Reihenfolge: Owner > Admin > Mod > MVP > VIP > Spieler
    public static final LinkedHashMap<String, Rank> RANKS = new LinkedHashMap<>();
    static {
        RANKS.put("owner",   new Rank("owner",   "§4§l[Owner] §r",  "§4[Owner] ",  TextColor.color(0xCC0000), 1));
        RANKS.put("admin",   new Rank("admin",   "§c§l[Admin] §r",  "§c[Admin] ",  NamedTextColor.RED,        2));
        RANKS.put("mod",     new Rank("mod",     "§9§l[Mod] §r",    "§9[Mod] ",    NamedTextColor.BLUE,       3));
        RANKS.put("mvp",     new Rank("mvp",     "§6§l[MVP] §r",    "§6[MVP] ",    NamedTextColor.GOLD,       4));
        RANKS.put("vip",     new Rank("vip",     "§a[VIP] §r",      "§a[VIP] ",    NamedTextColor.GREEN,      5));
        RANKS.put("spieler", new Rank("spieler", "",                "",            NamedTextColor.WHITE,      6));
    }

    public RankManager(PHLobby plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "ranks.yml");
        this.data     = YamlConfiguration.loadConfiguration(dataFile);
        load();
    }

    // ── Laden / Speichern ────────────────────────────────────────────────

    private void load() {
        for (String key : data.getKeys(false)) {
            try {
                UUID uuid  = UUID.fromString(key);
                String rank = data.getString(key + ".rank", "spieler");
                if (RANKS.containsKey(rank)) playerRanks.put(uuid, rank);
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info(playerRanks.size() + " Rang-Eintrag/Einträge geladen.");
    }

    public void setRank(UUID uuid, String playerName, String rankId) {
        if (!RANKS.containsKey(rankId)) return;
        playerRanks.put(uuid, rankId);
        data.set(uuid + ".rank", rankId);
        data.set(uuid + ".name", playerName);
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Rang konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ── Abfragemethoden ──────────────────────────────────────────────────

    public Rank getRank(UUID uuid) {
        return RANKS.getOrDefault(playerRanks.getOrDefault(uuid, "spieler"), RANKS.get("spieler"));
    }

    public String getRankId(UUID uuid) {
        return playerRanks.getOrDefault(uuid, "spieler");
    }

    // ── Tab-Listen-Name setzen ───────────────────────────────────────────

    public void applyTabName(Player player) {
        Rank rank = getRank(player.getUniqueId());
        String tabName = rank.tabPrefix + player.getName();
        player.playerListName(Component.text(tabName));
    }
}
