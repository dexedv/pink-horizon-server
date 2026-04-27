package de.pinkhorizon.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "ph-velocity",
    name = "PH-Velocity",
    version = "1.0.0",
    authors = {"PinkHorizon"}
)
public class PHVelocity {

    private static final TextColor PINK       = TextColor.color(0xFF69B4);
    private static final TextColor LIGHT_PINK = TextColor.color(0xFFB6C1);

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public PHVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        // /hub Command
        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("hub").plugin(this).build(),
            new HubCommand()
        );

        // Stündlicher Vote-Broadcast
        server.getScheduler()
            .buildTask(this, this::broadcastVote)
            .delay(1, TimeUnit.HOURS)
            .repeat(1, TimeUnit.HOURS)
            .schedule();

        logger.info("PH-Velocity gestartet – /hub + stündlicher Broadcast aktiv.");
    }

    private void broadcastVote() {
        Component line = Component.text("─────────────────────────────────", PINK);

        Component voteCmd = Component.text("/vote", LIGHT_PINK, TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/vote"))
            .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Voten!", NamedTextColor.GRAY)));

        Component hubCmd = Component.text("/hub", LIGHT_PINK, TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/hub"))
            .hoverEvent(HoverEvent.showText(Component.text("Zur Lobby teleportieren!", NamedTextColor.GRAY)));

        Component msg1 = Component.text(" \uD83D\uDC96 ", NamedTextColor.WHITE)
            .append(Component.text("Unterstütze Pink Horizon!", PINK, TextDecoration.BOLD));
        Component msg2 = Component.text("   Nutze ", NamedTextColor.GRAY)
            .append(voteCmd)
            .append(Component.text(" und verdiene VoteCoins!", NamedTextColor.GRAY));
        Component msg3 = Component.text("   ⚠ Voten ist nur in der ", NamedTextColor.YELLOW)
            .append(Component.text("Lobby", LIGHT_PINK, TextDecoration.BOLD))
            .append(Component.text(" möglich!", NamedTextColor.YELLOW));
        Component msg4 = Component.text("   Nicht in der Lobby? Komm mit ", NamedTextColor.GRAY)
            .append(hubCmd)
            .append(Component.text(" zurück!", NamedTextColor.GRAY));

        for (Player player : server.getAllPlayers()) {
            player.sendMessage(line);
            player.sendMessage(msg1);
            player.sendMessage(msg2);
            player.sendMessage(msg3);
            player.sendMessage(msg4);
            player.sendMessage(line);
        }
    }

    private class HubCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(Component.text("Nur für Spieler.", NamedTextColor.RED));
                return;
            }
            Optional<RegisteredServer> lobby = server.getServer("lobby");
            if (lobby.isEmpty()) {
                player.sendMessage(Component.text("Lobby ist aktuell nicht erreichbar.", NamedTextColor.RED));
                return;
            }
            // Bereits in der Lobby? Trotzdem senden – Paper-Plugin teleportiert zum Spawn
            player.createConnectionRequest(lobby.get()).fireAndForget();
            player.sendMessage(Component.text("✦ Du wirst zur Lobby verbunden...", PINK));
        }
    }
}
