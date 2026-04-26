package de.pinkhorizon.generators.listeners;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.managers.MoneyManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Formatiert den Chat: [LuckPerms-Prefix] [Prestige-Badge] Name: Nachricht
 */
public class ChatListener implements Listener {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    public ChatListener(PHGenerators plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());

        Component prefix = getLuckPermsPrefix(player);
        Component prestige = buildPrestigeBadge(data);
        Component name = Component.text(player.getName(), NamedTextColor.WHITE);

        // [Prefix] [P5] Spielername: Nachricht
        Component displayName = prefix.append(prestige).append(name);

        e.renderer((source, sourceDisplayName, message, viewer) ->
                displayName
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message)
        );
    }

    // ── Hilfsmethoden (auch von ScoreboardManager genutzt) ─────────────────

    /** Gibt den LuckPerms-Prefix als Component zurück (leer wenn nicht verfügbar). */
    public static Component getLuckPermsPrefix(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String raw = user.getCachedData().getMetaData().getPrefix();
                if (raw != null && !raw.isEmpty()) {
                    return LEGACY.deserialize(raw)
                            .append(Component.text(" "));
                }
            }
        } catch (Exception ignored) {}
        return Component.empty();
    }

    /** Gibt den Prestige-Badge als Component zurück (leer bei Prestige 0). */
    public static Component buildPrestigeBadge(PlayerData data) {
        if (data == null || data.getPrestige() <= 0) return Component.empty();
        return MM.deserialize("<dark_gray>[<light_purple>P" + data.getPrestige()
                + "</light_purple>]</dark_gray> ");
    }
}
