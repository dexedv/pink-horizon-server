package de.pinkhorizon.core.listeners;

import de.pinkhorizon.core.PHCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final PHCore plugin;

    public ChatListener(PHCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String format = plugin.getConfig().getString("chat.format", "&7{player}: &f{message}");
        String playerName = event.getPlayer().getName();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        String formatted = format
                .replace("{player}", playerName)
                .replace("{rank}", "Spieler")
                .replace("{message}", message)
                .replace("&", "\u00a7");

        event.renderer((source, sourceDisplayName, msg, viewer) ->
                Component.text(formatted));
    }
}
