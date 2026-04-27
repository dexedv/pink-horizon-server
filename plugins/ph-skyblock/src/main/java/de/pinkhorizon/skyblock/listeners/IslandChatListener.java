package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import de.pinkhorizon.skyblock.enums.IslandRank;
import de.pinkhorizon.skyblock.enums.TitleType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@SuppressWarnings("deprecation")
public class IslandChatListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;

    public IslandChatListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());

        // ── Insel-Chat ────────────────────────────────────────────────────────
        if (sp != null && sp.isIslandChat()) {
            Island island = plugin.getIslandManager().getIslandOfPlayer(player.getUniqueId());
            if (island == null) {
                sp.setIslandChat(false);
            } else {
                e.setCancelled(true);
                var component = plugin.msgNoPrefix("island-chat-format",
                    "name", player.getName(), "msg", e.getMessage());
                Player owner = Bukkit.getPlayer(island.getOwnerUuid());
                if (owner != null) owner.sendMessage(component);
                for (Island.IslandMember m : island.getMembers()) {
                    Player mp = Bukkit.getPlayer(m.uuid());
                    if (mp != null) mp.sendMessage(component);
                }
                plugin.getLogger().info("[Insel-Chat] " + player.getName() + ": " + e.getMessage());
                return;
            }
        }

        // ── Globaler Chat: Rang + Titel formatieren ───────────────────────────
        Island island  = plugin.getIslandManager().getIslandOfPlayer(player.getUniqueId());
        long islandLvl = island != null ? island.getLevel() : 0;
        IslandRank rank = IslandRank.of(islandLvl);

        TitleType title = plugin.getTitleManager().getActiveTitle(player.getUniqueId());
        String titlePrefix = (title != null && title != TitleType.KEIN_TITEL)
            ? title.getChatPrefix() : "";

        // Format: [Rang] [Titel] Farbiger Name: Nachricht
        String nameColor = rank.getNameColor();
        String format = rank.getBadge()
            + (titlePrefix.isEmpty() ? "" : " " + titlePrefix.trim())
            + " " + nameColor + "%s"
            + "§7: §f%s";

        e.setFormat(format);
    }
}
