package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClaimManager {

    private final PHSurvival plugin;
    // Map: "world:chunkX:chunkZ" -> Besitzer-UUID
    private final Map<String, UUID> claims = new HashMap<>();
    // Map: UUID -> Set<claim-keys>
    private final Map<UUID, Set<String>> playerClaims = new HashMap<>();
    // Map: claim-key -> Set<trusted UUIDs>
    private final Map<String, Set<UUID>> trust = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    public ClaimManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
    }

    public String getKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public boolean isClaimed(Chunk chunk) {
        return claims.containsKey(getKey(chunk));
    }

    public UUID getOwner(Chunk chunk) {
        return claims.get(getKey(chunk));
    }

    public boolean isOwner(Chunk chunk, UUID uuid) {
        return uuid.equals(getOwner(chunk));
    }

    public int getClaimCount(UUID uuid) {
        return playerClaims.getOrDefault(uuid, Set.of()).size();
    }

    public Set<String> getPlayerClaims(UUID uuid) {
        return playerClaims.getOrDefault(uuid, new HashSet<>());
    }

    /** Claim with explicit max (from SurvivalRankManager) */
    public boolean claim(Chunk chunk, UUID owner, int maxClaims) {
        if (getClaimCount(owner) >= maxClaims) return false;
        if (isClaimed(chunk)) return false;

        String key = getKey(chunk);
        claims.put(key, owner);
        playerClaims.computeIfAbsent(owner, k -> new HashSet<>()).add(key);
        return true;
    }

    /** Legacy claim (reads max from config) */
    public boolean claim(Chunk chunk, UUID owner) {
        int maxClaims = plugin.getConfig().getInt("claims.max-claims-per-player", 10);
        return claim(chunk, owner, maxClaims);
    }

    public boolean unclaim(Chunk chunk, UUID requester) {
        if (!isOwner(chunk, requester)) return false;
        String key = getKey(chunk);
        claims.remove(key);
        Set<String> playerSet = playerClaims.get(requester);
        if (playerSet != null) playerSet.remove(key);
        trust.remove(key);
        return true;
    }

    // ---- Trust ----

    public void trustPlayer(Chunk chunk, UUID trusted) {
        String key = getKey(chunk);
        trust.computeIfAbsent(key, k -> new HashSet<>()).add(trusted);
        saveTrust(key);
    }

    public void untrustPlayer(Chunk chunk, UUID trusted) {
        String key = getKey(chunk);
        Set<UUID> set = trust.get(key);
        if (set != null) {
            set.remove(trusted);
            if (set.isEmpty()) {
                trust.remove(key);
                data.set("trust." + key, null);
            } else {
                saveTrust(key);
            }
            saveFileSilent();
        }
    }

    public boolean isTrusted(Chunk chunk, UUID player) {
        if (isOwner(chunk, player)) return true;
        String key = getKey(chunk);
        Set<UUID> set = trust.get(key);
        return set != null && set.contains(player);
    }

    public Set<UUID> getTrusted(Chunk chunk) {
        return trust.getOrDefault(getKey(chunk), new HashSet<>());
    }

    private void saveTrust(String key) {
        Set<UUID> set = trust.get(key);
        if (set == null || set.isEmpty()) return;
        List<String> list = new ArrayList<>();
        for (UUID uuid : set) list.add(uuid.toString());
        data.set("trust." + key, list);
        saveFileSilent();
    }

    private void saveFileSilent() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Claims konnten nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "claims.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (data.contains("claims")) {
            for (String key : data.getConfigurationSection("claims").getKeys(false)) {
                UUID owner = UUID.fromString(data.getString("claims." + key));
                claims.put(key, owner);
                playerClaims.computeIfAbsent(owner, k -> new HashSet<>()).add(key);
            }
        }
        if (data.contains("trust")) {
            for (String key : data.getConfigurationSection("trust").getKeys(false)) {
                List<String> uuidList = data.getStringList("trust." + key);
                Set<UUID> set = new HashSet<>();
                for (String uuidStr : uuidList) {
                    try { set.add(UUID.fromString(uuidStr)); } catch (IllegalArgumentException ignored) {}
                }
                if (!set.isEmpty()) trust.put(key, set);
            }
        }
    }

    public void save() {
        claims.forEach((key, uuid) -> data.set("claims." + key, uuid.toString()));
        trust.forEach((key, set) -> {
            List<String> list = new ArrayList<>();
            for (UUID uuid : set) list.add(uuid.toString());
            data.set("trust." + key, list);
        });
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Claims konnten nicht gespeichert werden: " + e.getMessage());
        }
    }
}
