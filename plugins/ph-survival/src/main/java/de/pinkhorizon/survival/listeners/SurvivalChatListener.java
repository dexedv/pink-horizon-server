package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SurvivalRankManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurvivalChatListener implements Listener {

    private final PHSurvival plugin;
    private final Map<UUID, Long>    lastMessageTime = new HashMap<>();
    private final Map<UUID, String>  lastMessage     = new HashMap<>();
    private final Map<UUID, Integer> repeatCount     = new HashMap<>();

    private static final long COOLDOWN_MS = 1500;
    private static final int  MAX_REPEATS = 3;

    private static final LegacyComponentSerializer LEGACY =
        LegacyComponentSerializer.legacySection();

    public SurvivalChatListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        Player player  = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        UUID   uuid    = player.getUniqueId();
        long   now     = System.currentTimeMillis();

        // AFK zurücksetzen
        plugin.getAfkManager().resetAfk(player);

        // Spam-Cooldown (Admins ausgenommen)
        if (!player.hasPermission("survival.admin")) {
            long last = lastMessageTime.getOrDefault(uuid, 0L);
            if (now - last < COOLDOWN_MS) {
                player.sendMessage(Component.text("Bitte warte kurz zwischen den Nachrichten!", NamedTextColor.RED));
                return;
            }

            // Wiederholungsschutz
            String prev  = lastMessage.getOrDefault(uuid, "");
            int    count = message.equalsIgnoreCase(prev) ? repeatCount.getOrDefault(uuid, 0) + 1 : 0;
            repeatCount.put(uuid, count);
            if (count >= MAX_REPEATS) {
                player.sendMessage(Component.text("Bitte sende nicht dieselbe Nachricht mehrmals!", NamedTextColor.RED));
                return;
            }
        }

        lastMessageTime.put(uuid, now);
        lastMessage.put(uuid, message);

        broadcast(player, message);
    }

    private void broadcast(Player player, String message) {
        SurvivalRankManager.Rank rank = plugin.getRankManager().getRank(player.getUniqueId());
        boolean isAfk = plugin.getAfkManager().isAfk(player.getUniqueId());

        // Rang-Badge (z.B. §4§l[Owner])
        Component badge = rank.chatPrefix.isBlank()
            ? Component.empty()
            : LEGACY.deserialize(rank.chatPrefix.trim())
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" "));

        // AFK-Tag
        Component afkTag = isAfk
            ? Component.text("[AFK] ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            : Component.empty();

        // Spielername – klickbar (schlägt /msg vor) + Hover-Info
        Component hoverText = Component.text("Rang: ", NamedTextColor.GRAY)
            .append(LEGACY.deserialize(rank.chatPrefix.trim().isEmpty() ? "Spieler" : rank.chatPrefix.trim()))
            .appendNewline()
            .append(Component.text("Klicken zum Anschreiben", NamedTextColor.DARK_GRAY));

        Component playerName = Component.text(player.getName(), rank.nameColor)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, false)
            .hoverEvent(HoverEvent.showText(hoverText))
            .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "));

        // Trennzeichen + Nachricht
        Component separator = Component.text(" » ", NamedTextColor.DARK_GRAY);
        Component msg       = Component.text(message, NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false);

        Component full = afkTag
            .append(badge)
            .append(playerName)
            .append(separator)
            .append(msg);

        player.getServer().broadcast(full);
    }
}
