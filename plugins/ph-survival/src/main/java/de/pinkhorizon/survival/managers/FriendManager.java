package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FriendManager {

    private final PHSurvival plugin;
    private File dataFile;
    private YamlConfiguration data;

    public FriendManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    public Set<UUID> getFriends(UUID uuid) {
        Set<UUID> result = new HashSet<>();
        for (String s : data.getStringList("friends." + uuid)) {
            try { result.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public boolean areFriends(UUID a, UUID b) {
        return getFriends(a).contains(b);
    }

    public void addFriend(UUID a, UUID b) {
        addToList("friends." + a, b.toString());
        addToList("friends." + b, a.toString());
        removeRequest(a, b);
        removeRequest(b, a);
        save();
    }

    public void removeFriend(UUID a, UUID b) {
        removeFromList("friends." + a, b.toString());
        removeFromList("friends." + b, a.toString());
        save();
    }

    public boolean hasPendingRequest(UUID from, UUID to) {
        return data.getStringList("requests." + from).contains(to.toString());
    }

    public void sendRequest(UUID from, UUID to) {
        addToList("requests." + from, to.toString());
        save();
    }

    public void removeRequest(UUID from, UUID to) {
        removeFromList("requests." + from, to.toString());
        save();
    }

    public List<UUID> getIncomingRequests(UUID target) {
        List<UUID> result = new ArrayList<>();
        var section = data.getConfigurationSection("requests");
        if (section == null) return result;
        for (String senderStr : section.getKeys(false)) {
            try {
                UUID sender = UUID.fromString(senderStr);
                if (data.getStringList("requests." + sender).contains(target.toString())) {
                    result.add(sender);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    private void addToList(String path, String value) {
        List<String> list = new ArrayList<>(data.getStringList(path));
        if (!list.contains(value)) list.add(value);
        data.set(path, list);
    }

    private void removeFromList(String path, String value) {
        List<String> list = new ArrayList<>(data.getStringList(path));
        list.remove(value);
        data.set(path, list);
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "friends.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try { data.save(dataFile); }
        catch (IOException ex) { plugin.getLogger().warning("Friends konnten nicht gespeichert werden: " + ex.getMessage()); }
    }
}
