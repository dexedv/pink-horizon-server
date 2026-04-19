package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SurvivalRankManager;
import io.papermc.paper.event.player.AsyncChatEvent;
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
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final Map<UUID, String> lastMessage = new HashMap<>();
    private final Map<UUID, Integer> repeatCount = new HashMap<>();

    private static final long COOLDOWN_MS = 1500;
    private static final int MAX_REPEATS = 3;

    public SurvivalChatListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) {
            sendFormatted(player, PlainTextComponentSerializer.plainText().serialize(event.message()));
            event.setCancelled(true);
            return;
        }

        // AFK zurücksetzen
        plugin.getAfkManager().resetAfk(player);

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Spam-Cooldown
        long last = lastMessageTime.getOrDefault(uuid, 0L);
        if (now - last < COOLDOWN_MS) {
            event.setCancelled(true);
            player.sendMessage("§cBitte warte kurz zwischen den Nachrichten!");
            return;
        }

        // Wiederholungsschutz
        String prev = lastMessage.getOrDefault(uuid, "");
        if (message.equalsIgnoreCase(prev)) {
            int count = repeatCount.getOrDefault(uuid, 0) + 1;
            repeatCount.put(uuid, count);
            if (count >= MAX_REPEATS) {
                event.setCancelled(true);
                player.sendMessage("§cBitte sende nicht dieselbe Nachricht mehrmals!");
                return;
            }
        } else {
            repeatCount.put(uuid, 0);
        }

        lastMessageTime.put(uuid, now);
        lastMessage.put(uuid, message);

        event.setCancelled(true);
        sendFormatted(player, message);
    }

    private void sendFormatted(Player player, String message) {
        SurvivalRankManager.Rank rank = plugin.getRankManager().getRank(player.getUniqueId());
        String afkTag = plugin.getAfkManager().isAfk(player.getUniqueId()) ? "§7[AFK] " : "";
        String formatted = "§8[Survival] §r" + afkTag + rank.chatPrefix + player.getName()
                + " §8» §f" + message;
        player.getServer().broadcastMessage(formatted);
    }
}
