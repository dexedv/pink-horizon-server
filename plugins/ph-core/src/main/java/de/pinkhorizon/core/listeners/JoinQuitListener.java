package de.pinkhorizon.core.listeners;

import de.pinkhorizon.core.PHCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class JoinQuitListener implements Listener {

    private final PHCore plugin;

    public JoinQuitListener(PHCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(Component.text(event.getPlayer().getName() + " hat den Server betreten.", NamedTextColor.GREEN));

        String sql = plugin.getDatabaseManager().getDbType().equals("mysql")
            ? "INSERT INTO players (uuid, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), last_join = CURRENT_TIMESTAMP"
            : "INSERT INTO players (uuid, name) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, last_join = CURRENT_TIMESTAMP";

        try (var con  = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, event.getPlayer().getUniqueId().toString());
            stmt.setString(2, event.getPlayer().getName());
            stmt.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB-Fehler bei Join: " + e.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(Component.text(event.getPlayer().getName() + " hat den Server verlassen.", NamedTextColor.RED));
    }
}
