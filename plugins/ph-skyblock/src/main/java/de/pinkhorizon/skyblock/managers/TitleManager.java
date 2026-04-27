package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.database.GeneratorRepository;
import de.pinkhorizon.skyblock.enums.TitleType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet aktive Titel der Spieler.
 * Eigentümerschaft wird über AchievementManager geprüft ("title_<id>").
 */
public class TitleManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;
    private final GeneratorRepository repo;

    /** UUID → aktiver TitleType */
    private final Map<UUID, TitleType> activeCache = new HashMap<>();

    public TitleManager(PHSkyBlock plugin, GeneratorRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    public void loadPlayer(UUID uuid) {
        String id = repo.getActiveTitle(uuid);
        activeCache.put(uuid, TitleType.byId(id));
    }

    public void unloadPlayer(UUID uuid) {
        activeCache.remove(uuid);
    }

    // ── Aktiver Titel ─────────────────────────────────────────────────────────

    public TitleType getActiveTitle(UUID uuid) {
        return activeCache.getOrDefault(uuid, TitleType.KEIN_TITEL);
    }

    /**
     * Setzt den aktiven Titel eines Spielers.
     * Prüft vorher ob der Spieler diesen Titel besitzt.
     */
    public boolean setActiveTitle(Player player, TitleType title) {
        if (!plugin.getAchievementManager().ownsTitle(player.getUniqueId(), title)) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Du besitzt diesen Titel nicht!"));
            return false;
        }
        activeCache.put(player.getUniqueId(), title);
        repo.setActiveTitle(player.getUniqueId(), title.getId());

        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<green>Aktiver Titel gesetzt: "
            + (title == TitleType.KEIN_TITEL ? "<gray>Kein Titel" : title.getChatPrefix().trim())));
        return true;
    }

    /**
     * Chat-Präfix für den Spieler: "§d[PinkStar] §fSpielername: Nachricht"
     * Gibt einen leeren String zurück wenn kein Titel aktiv.
     */
    public String getChatPrefix(UUID uuid) {
        TitleType title = getActiveTitle(uuid);
        return title == TitleType.KEIN_TITEL ? "" : title.getChatPrefix();
    }

    // ── Kauf ─────────────────────────────────────────────────────────────────

    /**
     * Kauft einen Titel für Coins. Gibt true bei Erfolg zurück.
     */
    public boolean buyTitle(Player player, TitleType title) {
        UUID uuid = player.getUniqueId();

        if (!title.isBuyable()) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Dieser Titel ist nicht kaufbar."));
            return false;
        }

        if (plugin.getAchievementManager().ownsTitle(uuid, title)) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<yellow>Du besitzt diesen Titel bereits."));
            return false;
        }

        if (!plugin.getCoinManager().deductCoins(uuid, title.getBuyPrice())) {
            player.sendMessage(MM.deserialize(
                "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
                + "<red>Nicht genug Coins! Benötigt: <gold>"
                + String.format("%,d", title.getBuyPrice())));
            return false;
        }

        // Ownership speichern
        plugin.getAchievementManager().grantTitleOwnership(uuid, title);

        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<green>Titel gekauft: " + title.getChatPrefix().trim()));
        player.playSound(player.getLocation(),
            org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        return true;
    }
}
