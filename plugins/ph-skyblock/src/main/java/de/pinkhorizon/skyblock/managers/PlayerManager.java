package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import de.pinkhorizon.skyblock.database.IslandRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final PHSkyBlock plugin;
    private final IslandRepository repo;
    private final Map<UUID, SkyPlayer> cache = new ConcurrentHashMap<>();

    public PlayerManager(PHSkyBlock plugin, IslandRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    public SkyPlayer loadPlayer(UUID uuid, String name) {
        SkyPlayer sp = repo.loadOrCreatePlayer(uuid, name);
        cache.put(uuid, sp);
        return sp;
    }

    public SkyPlayer getPlayer(UUID uuid) {
        return cache.get(uuid);
    }

    public void saveAndUnload(UUID uuid) {
        SkyPlayer sp = cache.remove(uuid);
        if (sp != null) {
            sp.setLastSeen(System.currentTimeMillis());
            repo.savePlayer(sp);
        }
    }
}
