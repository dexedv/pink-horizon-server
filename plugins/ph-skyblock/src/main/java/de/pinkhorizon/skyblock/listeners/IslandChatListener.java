package de.pinkhorizon.skyblock.listeners;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@SuppressWarnings("deprecation")
public class IslandChatListener implements Listener {

    private final PHSkyBlock plugin;

    public IslandChatListener(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null || !sp.isIslandChat()) return;

        Island island = plugin.getIslandManager().getIslandOfPlayer(player.getUniqueId());
        if (island == null) {
            sp.setIslandChat(false);
            return;
        }

        e.setCancelled(true);
        String msg = e.getMessage();
        var component = plugin.msgNoPrefix("island-chat-format",
            "name", player.getName(), "msg", msg);

        // An Owner + alle Mitglieder senden
        Player owner = Bukkit.getPlayer(island.getOwnerUuid());
        if (owner != null) owner.sendMessage(component);
        for (Island.IslandMember m : island.getMembers()) {
            Player mp = Bukkit.getPlayer(m.uuid());
            if (mp != null) mp.sendMessage(component);
        }
        // Konsolenausgabe
        plugin.getLogger().info("[Insel-Chat] " + player.getName() + ": " + msg);
    }
}
