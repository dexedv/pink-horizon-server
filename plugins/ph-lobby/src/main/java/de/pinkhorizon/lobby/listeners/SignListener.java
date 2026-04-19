package de.pinkhorizon.lobby.listeners;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.pinkhorizon.lobby.PHLobby;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignListener implements Listener {

    private final PHLobby plugin;

    public SignListener(PHLobby plugin) {
        this.plugin = plugin;
        // BungeeCord/Velocity Messaging-Kanal registrieren
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;

        String firstLine = org.bukkit.ChatColor.stripColor(
                sign.getSide(Side.FRONT).line(0).toString()
        );

        if (!"[PH-Nav]".equalsIgnoreCase(firstLine.trim())) return;

        String serverLine = org.bukkit.ChatColor.stripColor(
                sign.getSide(Side.FRONT).line(1).toString()
        ).trim().toLowerCase();

        String target = switch (serverLine) {
            case "survival"  -> "survival";
            case "skyblock"  -> "skyblock";
            case "minigames" -> "minigames";
            case "lobby"     -> "lobby";
            default          -> null;
        };

        if (target == null) return;

        sendToServer(event.getPlayer(), target);
        event.getPlayer().sendMessage("\u00a7dDu wirst zu \u00a7f" + capitalize(target) + "\u00a7d verbunden...");
    }

    private void sendToServer(Player player, String server) {
        @SuppressWarnings("UnstableApiUsage")
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
