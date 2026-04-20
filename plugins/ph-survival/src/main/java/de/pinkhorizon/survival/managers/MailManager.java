package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MailManager {

    private static final int MAX_MAILS = 50;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public record Mail(String sender, String message, String timestamp, boolean read) {}

    private final PHSurvival plugin;
    private File dataFile;
    private YamlConfiguration data;

    public MailManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    public boolean send(String senderName, UUID recipientUuid, String message) {
        List<Mail> mails = getMails(recipientUuid);
        if (mails.size() >= MAX_MAILS) return false;
        mails.add(new Mail(senderName, message, LocalDateTime.now().format(FORMATTER), false));
        saveMails(recipientUuid, mails);
        return true;
    }

    public List<Mail> getMails(UUID uuid) {
        List<Mail> result = new ArrayList<>();
        List<?> list = data.getList("mails." + uuid);
        if (list == null) return result;
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            result.add(new Mail(
                (String) map.get("sender"),
                (String) map.get("message"),
                (String) map.get("timestamp"),
                Boolean.TRUE.equals(map.get("read"))
            ));
        }
        return result;
    }

    public int getUnreadCount(UUID uuid) {
        return (int) getMails(uuid).stream().filter(m -> !m.read()).count();
    }

    public void markAllRead(UUID uuid) {
        List<Mail> updated = getMails(uuid).stream()
            .map(m -> new Mail(m.sender(), m.message(), m.timestamp(), true))
            .toList();
        saveMails(uuid, updated);
    }

    public void clearMails(UUID uuid) {
        data.set("mails." + uuid, null);
        save();
    }

    private void saveMails(UUID uuid, List<Mail> mails) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Mail m : mails) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sender", m.sender());
            entry.put("message", m.message());
            entry.put("timestamp", m.timestamp());
            entry.put("read", m.read());
            list.add(entry);
        }
        data.set("mails." + uuid, list);
        save();
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "mail.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try { data.save(dataFile); }
        catch (IOException ex) { plugin.getLogger().warning("Mail konnte nicht gespeichert werden: " + ex.getMessage()); }
    }
}
