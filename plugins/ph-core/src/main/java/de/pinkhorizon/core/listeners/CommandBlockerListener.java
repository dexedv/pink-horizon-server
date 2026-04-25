package de.pinkhorizon.core.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;

/**
 * Blockiert server-interne und admin-only Commands für normale Spieler.
 * Admins (survival.admin / lobby.admin / op) sind ausgenommen.
 */
public class CommandBlockerListener implements Listener {

    /** Exakt geblockte Commands (nach dem ersten Wort, lowercase, ohne /). */
    private static final Set<String> BLOCKED = Set.of(
        // Plugin-Infos
        "pl", "plugins", "bukkit:pl", "bukkit:plugins",
        "paper:pl", "paper:plugins", "spigot:pl", "spigot:plugins",

        // Versionsinformationen
        "ver", "version", "bukkit:ver", "bukkit:version",
        "paper:ver", "paper:version", "about",
        "icanhasbukkit",

        // Server-Verwaltung (nur Konsole/Admin)
        "reload", "rl", "bukkit:reload", "paper:reload",
        "restart", "stop",
        "timings", "paper:timings",

        // Op / Whitelist
        "op", "deop",
        "whitelist",

        // Vanilla-Commands die durch eigene ersetzt sind
        "ban", "ban-ip", "pardon", "pardon-ip",
        "kick",
        "gamemode", "gm",
        "give",
        "tp", "teleport",
        "kill",
        "clear",
        "difficulty",
        "effect",
        "enchant",
        "experience", "xp",
        "seed",
        "setblock", "fill", "clone",
        "summon",
        "time", "weather",
        "gamerule",
        "say",
        "title",
        "scoreboard",
        "team",
        "tag",
        "function",
        "execute",
        "forceload",
        "locate", "locatebiome",
        "loot",
        "perf", "tps",

        // Minecraft-Namespace
        "minecraft:tp", "minecraft:teleport",
        "minecraft:give", "minecraft:kill", "minecraft:clear",
        "minecraft:gamemode", "minecraft:difficulty",
        "minecraft:ban", "minecraft:ban-ip", "minecraft:kick",
        "minecraft:op", "minecraft:deop", "minecraft:whitelist",
        "minecraft:reload", "minecraft:stop",
        "minecraft:time", "minecraft:weather",
        "minecraft:summon", "minecraft:setblock", "minecraft:fill",
        "minecraft:seed", "minecraft:effect", "minecraft:enchant",
        "minecraft:experience", "minecraft:xp",
        "minecraft:say", "minecraft:title", "minecraft:scoreboard",
        "minecraft:gamerule", "minecraft:function", "minecraft:execute",
        "minecraft:locate"
    );

    /** Präfixe – alles was damit anfängt wird geblockt. */
    private static final Set<String> BLOCKED_PREFIXES = Set.of(
        "bukkit:", "paper:", "spigot:", "minecraft:debug",
        "luckperms:", "lp "
    );

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Admins / OPs dürfen alles
        if (player.isOp()
                || player.hasPermission("survival.admin")
                || player.hasPermission("lobby.admin")) {
            return;
        }

        String raw   = event.getMessage().substring(1); // ohne führendes /
        String lower = raw.toLowerCase();
        String cmd   = lower.split(" ")[0];             // erstes Wort

        // Exakte Übereinstimmung
        if (BLOCKED.contains(cmd)) {
            deny(event, player);
            return;
        }

        // Präfix-Check
        for (String prefix : BLOCKED_PREFIXES) {
            if (lower.startsWith(prefix)) {
                deny(event, player);
                return;
            }
        }
    }

    private void deny(PlayerCommandPreprocessEvent event, Player player) {
        event.setCancelled(true);
        player.sendMessage(
            net.kyori.adventure.text.Component.text(
                "✖ Dieser Befehl ist nicht verfügbar.",
                net.kyori.adventure.text.format.NamedTextColor.RED
            )
        );
    }
}
