package de.pinkhorizon.lobby.commands;

import de.pinkhorizon.core.PHCore;
import de.pinkhorizon.lobby.PHLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Random;

public class SyncCommand implements CommandExecutor, TabCompleter {

    private final PHLobby plugin;
    private static final Random RANDOM = new Random();
    private static final long CODE_EXPIRE_MS = 15 * 60 * 1000L; // 15 Minuten

    public SyncCommand(PHLobby plugin) {
        this.plugin = plugin;
        createTable();
    }

    private void createTable() {
        try (Connection c = PHCore.getInstance().getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS discord_sync (
                     uuid        CHAR(36)     NOT NULL PRIMARY KEY,
                     mc_name     VARCHAR(16)  NOT NULL,
                     code        CHAR(5)      NOT NULL,
                     discord_id  VARCHAR(20)  DEFAULT NULL,
                     created_at  BIGINT       NOT NULL,
                     verified_at BIGINT       DEFAULT NULL,
                     UNIQUE KEY uq_code (code)
                 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                 """)) {
            st.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("SyncCommand.createTable: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur für Spieler.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("discord")) {
            player.sendMessage("§eVerwendung: §f/sync discord");
            return true;
        }

        String uuid = player.getUniqueId().toString();

        // Bereits verifiziert?
        if (isVerified(uuid)) {
            player.sendMessage("§a✔ §7Dein Discord ist bereits mit diesem Account verknüpft!");
            return true;
        }

        // Einzigartigen 5-stelligen Code generieren
        String code = generateUniqueCode();

        // Code in DB speichern (upsert – überschreibt alten Code)
        try (Connection c = PHCore.getInstance().getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO discord_sync (uuid, mc_name, code, created_at)
                 VALUES (?, ?, ?, ?)
                 ON DUPLICATE KEY UPDATE
                     mc_name    = VALUES(mc_name),
                     code       = VALUES(code),
                     created_at = VALUES(created_at),
                     verified_at = NULL,
                     discord_id  = NULL
                 """)) {
            st.setString(1, uuid);
            st.setString(2, player.getName());
            st.setString(3, code);
            st.setLong(4, System.currentTimeMillis());
            st.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("SyncCommand.onCommand: " + e.getMessage());
            player.sendMessage("§cFehler beim Erstellen des Codes. Versuche es erneut.");
            return true;
        }

        // Nachricht mit kopierbarem Code
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━ Discord Verifikation ━━━", TextColor.color(0xFF55FF), TextDecoration.BOLD));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Dein Code: ", NamedTextColor.GRAY)
            .append(Component.text(code, TextColor.color(0xFF55FF), TextDecoration.BOLD)
                .clickEvent(ClickEvent.copyToClipboard(code))));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Gehe auf unseren Discord und poste diesen", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Code im Kanal ", NamedTextColor.GRAY)
            .append(Component.text("#ingame-verification", TextColor.color(0x55FFFF))));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("⏰ Der Code ist 15 Minuten gültig.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", TextColor.color(0xFF55FF), TextDecoration.BOLD));
        player.sendMessage(Component.empty());

        return true;
    }

    private boolean isVerified(String uuid) {
        try (Connection c = PHCore.getInstance().getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT 1 FROM discord_sync WHERE uuid = ? AND verified_at IS NOT NULL")) {
            st.setString(1, uuid);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            String code = String.format("%05d", RANDOM.nextInt(100_000));
            if (!codeExists(code)) return code;
        }
        // Fallback: wahrscheinlich kein Duplikat
        return String.format("%05d", RANDOM.nextInt(100_000));
    }

    private boolean codeExists(String code) {
        try (Connection c = PHCore.getInstance().getDatabaseManager().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT 1 FROM discord_sync WHERE code = ? AND verified_at IS NULL AND created_at > ?")) {
            st.setString(1, code);
            st.setLong(2, System.currentTimeMillis() - CODE_EXPIRE_MS);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("discord");
        return List.of();
    }
}
