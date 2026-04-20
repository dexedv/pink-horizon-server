package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NpcListener implements Listener {

    private final PHSurvival plugin;

    public NpcListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Integer npcId = plugin.getNpcManager().getNpcIdByEntityUuid(event.getRightClicked().getUniqueId());
        if (npcId == null) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        for (String cmd : plugin.getNpcManager().getCommands(npcId)) {
            if (cmd.startsWith("[console] ")) {
                plugin.getServer().dispatchCommand(
                    plugin.getServer().getConsoleSender(),
                    cmd.substring("[console] ".length()).replace("{player}", player.getName())
                );
            } else {
                player.performCommand(cmd.replace("{player}", player.getName()));
            }
        }
    }
}
