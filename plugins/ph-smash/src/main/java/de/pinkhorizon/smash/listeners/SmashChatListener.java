package de.pinkhorizon.smash.listeners;

import de.pinkhorizon.smash.PHSmash;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class SmashChatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final PHSmash plugin;

    public SmashChatListener(PHSmash plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        String lpPrefix  = getLpPrefix(player);
        String prestige  = plugin.getPrestigeManager().getPrestigeDisplay(player.getUniqueId());
        int    bossLevel = plugin.getPlayerDataManager().getPersonalBossLevel(player.getUniqueId());
        String nameColor = rankNameColor(bossLevel);

        // Prefix-Komponente aufbauen: [LP-Rang] [Prestige] Name:
        StringBuilder prefix = new StringBuilder();
        if (!lpPrefix.isEmpty()) prefix.append(lpPrefix).append(" ");
        if (!prestige.isEmpty()) prefix.append(prestige).append(" ");
        prefix.append(nameColor).append(player.getName()).append("§8: §f");

        Component prefixComp = LEGACY.deserialize(prefix.toString());

        event.renderer((source, sourceDisplayName, message, viewer) ->
            prefixComp.append(message));
    }

    // ── LuckPerms-Prefix holen ────────────────────────────────────────────

    public static String getLpPrefix(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String prefix = user.getCachedData().getMetaData().getPrefix();
                if (prefix != null && !prefix.isBlank()) {
                    return translateColors(prefix);
                }
            }
        } catch (Throwable ignored) { }
        return "";
    }

    /** &x → §x Farb-Code-Übersetzung */
    public static String translateColors(String text) {
        if (text == null) return "";
        return text.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }

    /** Gibt die passende Namensfarbe für ein Boss-Level zurück */
    public static String rankNameColor(int level) {
        if (level >= 500) return "§4";
        if (level >= 251) return "§d";
        if (level >= 101) return "§5";
        if (level >= 51)  return "§b";
        if (level >= 26)  return "§6";
        if (level >= 11)  return "§a";
        return "§7";
    }
}
