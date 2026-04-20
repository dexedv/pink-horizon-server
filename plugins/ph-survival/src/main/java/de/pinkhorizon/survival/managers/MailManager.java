package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MailManager {

    private static final int MAX_MAILS = 50;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public record Mail(String sender, String message, String timestamp, boolean read) {}

    private final PHSurvival plugin;

    public MailManager(PHSurvival plugin) {
        this.plugin = plugin;
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    public boolean send(String senderName, UUID recipientUuid, String message) {
        if (getMails(recipientUuid).size() >= MAX_MAILS) return false;
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_mails (to_uuid, sender_name, message) VALUES (?,?,?)")) {
            st.setString(1, recipientUuid.toString());
            st.setString(2, senderName);
            st.setString(3, message);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MailManager.send: " + e.getMessage());
            return false;
        }
        return true;
    }

    public List<Mail> getMails(UUID uuid) {
        List<Mail> result = new ArrayList<>();
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT sender_name, message, sent_at, is_read FROM sv_mails WHERE to_uuid=? ORDER BY id ASC")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    String ts = rs.getTimestamp("sent_at")
                                  .toLocalDateTime()
                                  .format(FORMATTER);
                    result.add(new Mail(
                        rs.getString("sender_name"),
                        rs.getString("message"),
                        ts,
                        rs.getBoolean("is_read")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MailManager.getMails: " + e.getMessage());
        }
        return result;
    }

    public int getUnreadCount(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT COUNT(*) FROM sv_mails WHERE to_uuid=? AND is_read=FALSE")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MailManager.getUnreadCount: " + e.getMessage());
        }
        return 0;
    }

    public void markAllRead(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE sv_mails SET is_read=TRUE WHERE to_uuid=?")) {
            st.setString(1, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MailManager.markAllRead: " + e.getMessage());
        }
    }

    public void clearMails(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "DELETE FROM sv_mails WHERE to_uuid=?")) {
            st.setString(1, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MailManager.clearMails: " + e.getMessage());
        }
    }
}
