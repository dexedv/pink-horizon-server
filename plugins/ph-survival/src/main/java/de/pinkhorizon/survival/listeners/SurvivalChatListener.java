package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import de.pinkhorizon.survival.managers.SurvivalRankManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class SurvivalChatListener implements Listener {

    private final PHSurvival plugin;

    public SurvivalChatListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        SurvivalRankManager.Rank rank = plugin.getRankManager().getRank(player.getUniqueId());
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String formatted = "\u00a78[Survival] \u00a7r" + rank.chatPrefix + player.getName()
                + " \u00a78\u00bb \u00a7f" + message;
        player.getServer().broadcastMessage(formatted);
    }
}
