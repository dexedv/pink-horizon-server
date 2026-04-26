package de.pinkhorizon.generators.listeners;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

/**
 * Chat-Format: [Rang-Prefix] [P{prestige}] Name: Nachricht
 *
 * Rang-Prefix wird aus der LuckPerms-Gruppe des Spielers abgeleitet
 * (gleiche Mapping-Tabelle wie ph-lobby's RankManager).
 */
public class ChatListener implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    // Dieselbe Mapping-Tabelle wie ph-lobby RankManager
    // Key = LP-Gruppenname, Value = Legacy-Chat-Prefix
    private static final Map<String, String> GROUP_PREFIX = Map.of(
        "owner",     "§4§l[Owner] §r",
        "admin",     "§c§l[Admin] §r",
        "dev",       "§b§l[DEV] §r",
        "moderator", "§9§l[Mod] §r",
        "supporter", "§3§l[Support] §r",
        "vip",       "§6[VIP] §r"
    );

    // Namensfarbe je Gruppe (public für ScoreboardManager)
    public static final Map<String, TextColor> GROUP_COLOR = Map.of(
        "owner",     TextColor.color(0xCC0000),
        "admin",     NamedTextColor.RED,
        "dev",       TextColor.color(0x00CCCC),
        "moderator", NamedTextColor.BLUE,
        "supporter", TextColor.color(0x00AAAA),
        "vip",       NamedTextColor.GOLD
    );

    public ChatListener(PHGenerators plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());

        String group = getPrimaryGroup(player);
        Component prefix  = buildPrefix(group);
        Component prestige = buildPrestigeBadge(data);
        TextColor nameColor = GROUP_COLOR.getOrDefault(group, NamedTextColor.WHITE);
        Component name = Component.text(player.getName(), nameColor);

        // [§4§l[Owner]§r] [P5] Spielername: Nachricht
        Component displayName = prefix.append(prestige).append(name);

        e.renderer((source, sourceDisplayName, message, viewer) ->
                displayName
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message)
        );
    }

    // ── Hilfsmethoden (auch von ScoreboardManager verwendet) ───────────────

    /** Primäre LP-Gruppe des Spielers (online-Cache, kein DB-Call). */
    public static String getPrimaryGroup(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) return user.getPrimaryGroup();
        } catch (Exception ignored) {}
        return "default";
    }

    /** Rang-Prefix als Adventure-Component. */
    public static Component buildPrefix(String group) {
        String raw = GROUP_PREFIX.get(group);
        if (raw == null || raw.isEmpty()) return Component.empty();
        return LEGACY.deserialize(raw);
    }

    /** Prestige-Badge als Component (leer bei Prestige 0). */
    public static Component buildPrestigeBadge(PlayerData data) {
        if (data == null || data.getPrestige() <= 0) return Component.empty();
        return MM.deserialize("<dark_gray>[<light_purple>P" + data.getPrestige()
                + "</light_purple>]</dark_gray> ");
    }
}
