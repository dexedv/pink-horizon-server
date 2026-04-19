package de.pinkhorizon.survival.commands;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BaltopCommand implements CommandExecutor {

    private final PHSurvival plugin;

    public BaltopCommand(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sql = "SELECT uuid, name, coins FROM players ORDER BY coins DESC LIMIT 10";
        try (PreparedStatement stmt = PHCore.getInstance().getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            sender.sendMessage("\u00a7d\u00a7lTop 10 Spieler \u00a77(nach Coins):");
            int rank = 1;
            while (rs.next()) {
                String name = rs.getString("name");
                if (name == null) name = rs.getString("uuid");
                long coins = rs.getLong("coins");
                sender.sendMessage("\u00a77#" + rank + " \u00a7f" + name + " \u00a78- \u00a76" + coins + " Coins");
                rank++;
            }
            if (rank == 1) {
                sender.sendMessage("\u00a77Keine Spieler gefunden.");
            }
        } catch (SQLException e) {
            sender.sendMessage("\u00a7cFehler beim Laden der Daten: " + e.getMessage());
            plugin.getLogger().warning("BaltopCommand Fehler: " + e.getMessage());
        }
        return true;
    }
}
