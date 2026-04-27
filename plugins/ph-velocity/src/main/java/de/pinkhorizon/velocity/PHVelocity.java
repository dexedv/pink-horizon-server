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

        // /discord Command
        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("discord").plugin(this).build(),
            new DiscordCommand()
        );

        // Stündlicher Broadcast (abwechselnd Vote / Discord)
        server.getScheduler()
            .buildTask(this, this::broadcastRotating)
            .delay(1, TimeUnit.HOURS)
            .repeat(1, TimeUnit.HOURS)
            .schedule();

        logger.info("PH-Velocity gestartet – /hub + stündlicher Broadcast aktiv.");
    }

    private int broadcastTick = 0;

    private void broadcastRotating() {
        switch (broadcastTick % 6) {
            case 0 -> broadcastVote();
            case 1 -> broadcastDiscord();
            case 2 -> broadcastBugs();
            case 3 -> broadcastShop();
            case 4 -> broadcastIP();
            case 5 -> broadcastGamemodes();
        }
        broadcastTick++;
    }

    private void broadcastDiscord() {
        Component line = Component.text("─────────────────────────────────", PINK);

        Component discordLink = Component.text("discord.gg/j5C4h5XaK6", LIGHT_PINK, TextDecoration.BOLD)
            .clickEvent(ClickEvent.openUrl("https://discord.gg/j5C4h5XaK6"))
            .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Beitreten!", NamedTextColor.GRAY)));

        Component msg1 = Component.text(" 💬 ", NamedTextColor.WHITE)
            .append(Component.text("Wir haben einen Discord!", PINK, TextDecoration.BOLD));
        Component msg2 = Component.text("   Tritt uns bei: ", NamedTextColor.GRAY)
            .append(discordLink);
        Component msg3 = Component.text("   ✔ Du kannst dich auch in der ", NamedTextColor.GRAY)
            .append(Component.text("Lobby", LIGHT_PINK, TextDecoration.BOLD))
            .append(Component.text(" verifizieren!", NamedTextColor.GRAY));

        for (Player player : server.getAllPlayers()) {
            player.sendMessage(line);
            player.sendMessage(msg1);
            player.sendMessage(msg2);
            player.sendMessage(msg3);
            player.sendMessage(line);
        }
    }

    private void broadcastIP() {
        Component line = Component.text("─────────────────────────────────", PINK);

        Component ip = Component.text("play.pinkhorizon.fun", LIGHT_PINK, TextDecoration.BOLD)
            .clickEvent(ClickEvent.copyToClipboard("play.pinkhorizon.fun"))
            .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Kopieren!", NamedTextColor.GRAY)));

        Component msg1 = Component.text(" 🌐 ", NamedTextColor.WHITE)
            .append(Component.text("Magst du Pink Horizon?", PINK, TextDecoration.BOLD));
        Component msg2 = Component.text("   Teile unsere IP mit deinen Freunden: ", NamedTextColor.GRAY)
            .append(ip);
        Component msg3 = Component.text("   Je mehr Spieler, desto mehr Spaß! 💖", NamedTextColor.GRAY);

        for (Player player : server.getAllPlayers()) {
            player.sendMessage(line);
            player.sendMessage(msg1);
            player.sendMessage(msg2);
            player.sendMessage(msg3);
            player.sendMessage(line);
        }
    }

    private void broadcastGamemodes() {
        Component line = Component.text("─────────────────────────────────", PINK);

        Component hubCmd = Component.text("/hub", LIGHT_PINK, TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/hub"))
            .hoverEvent(HoverEvent.showText(Component.text("Zur Lobby teleportieren!", NamedTextColor.GRAY)));

        Component msg1 = Component.text(" 🎮 ", NamedTextColor.WHITE)
            .append(Component.text("Entdecke unsere Spielmodi!", PINK, TextDecoration.BOLD));
        Component msg2 = Component.text("   ⚔ ", NamedTextColor.WHITE)
            .append(Component.text("Survival", LIGHT_PINK, TextDecoration.BOLD))
            .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
            .append(Component.text("💥 ", NamedTextColor.WHITE))
            .append(Component.text("Smash the Boss", LIGHT_PINK, TextDecoration.BOLD))
            .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
            .append(Component.text("⚙ ", NamedTextColor.WHITE))
            .append(Component.text("IdleForge", LIGHT_PINK, TextDecoration.BOLD));
        Component msg3 = Component.text("   Wähle deinen Modus in der Lobby → ", NamedTextColor.GRAY)
            .append(hubCmd);

        for (Player player : server.getAllPlayers()) {
            player.sendMessage(line);
            player.sendMessage(msg1);
            player.sendMessage(msg2);
            player.sendMessage(msg3);
            player.sendMessage(line);
        }
    }

    private void broadcastShop() {
        Component line = Component.text("─────────────────────────────────", PINK);

        Component buyCmd = Component.text("/buy", LIGHT_PINK, TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/buy"))
            .hoverEvent(HoverEvent.showText(Component.text("Shop öffnen!", NamedTextColor.GRAY)));

        Component msg1 = Component.text(" 🛒 ", NamedTextColor.WHITE)
            .append(Component.text("Gefällt dir unser Server?", PINK, TextDecoration.BOLD));
        Component msg2 = Component.text("   Besuche unseren Shop mit ", NamedTextColor.GRAY)
            .append(buyCmd);
        Component msg3 = Component.text("   Mit jedem Kauf unterstützt du uns – danke! 💖", NamedTextColor.GRAY);

        for (Player player : server.getAllPlayers()) {
            player.sendMessage(line);
            player.sendMessage(msg1);
            player.sendMessage(msg2);
            player.sendMessage(msg3);
            player.sendMessage(line);
        }
    }

    private void broadcastBugs() {
        Component line = Component.text("─────────────────────────────────", PINK);

        Component discordLink = Component.text("discord.gg/j5C4h5XaK6", LIGHT_PINK, TextDecoration.BOLD)
            .clickEvent(ClickEvent.openUrl("https://discord.gg/j5C4h5XaK6"))
            .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Beitreten!", NamedTextColor.GRAY)));

        Component msg1 = Component.text(" 🐛 ", NamedTextColor.WHITE)
            .append(Component.text("Bugs, Fehler oder Ideen?", PINK, TextDecoration.BOLD));
        Component msg2 = Component.text("   Melde dich bei uns im Discord: ", NamedTextColor.GRAY)
            .append(discordLink);

        for (Player player : server.getAllPlayers()) {
            player.sendMessage(line);
            player.sendMessage(msg1);
            player.sendMessage(msg2);
            player.sendMessage(line);
        }
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

    private class DiscordCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            Component line = Component.text("─────────────────────────────────", PINK);

            Component discordLink = Component.text("discord.gg/j5C4h5XaK6", LIGHT_PINK, TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl("https://discord.gg/j5C4h5XaK6"))
                .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Beitreten!", NamedTextColor.GRAY)));

            Component hubCmd = Component.text("/hub", LIGHT_PINK, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/hub"))
                .hoverEvent(HoverEvent.showText(Component.text("Zur Lobby teleportieren!", NamedTextColor.GRAY)));

            invocation.source().sendMessage(line);
            invocation.source().sendMessage(
                Component.text(" 💬 ", NamedTextColor.WHITE)
                    .append(Component.text("Pink Horizon Discord", PINK, TextDecoration.BOLD)));
            invocation.source().sendMessage(
                Component.text("   Tritt uns bei: ", NamedTextColor.GRAY).append(discordLink));
            invocation.source().sendMessage(
                Component.text("   ✔ Verifizierung in der ", NamedTextColor.GRAY)
                    .append(Component.text("Lobby", LIGHT_PINK, TextDecoration.BOLD))
                    .append(Component.text(" möglich → ", NamedTextColor.GRAY))
                    .append(hubCmd));
            invocation.source().sendMessage(line);
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
