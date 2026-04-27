package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.Island;
import de.pinkhorizon.skyblock.data.SkyPlayer;
import de.pinkhorizon.skyblock.database.IslandRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IslandManager {

    private final PHSkyBlock plugin;
    private final IslandRepository repo;
    private final WorldManager worldManager;

    // Cache: owner UUID → Island
    private final Map<UUID, Island> islandByOwner = new ConcurrentHashMap<>();
    // Cache: island DB-ID → Island
    private final Map<Integer, Island> islandById = new ConcurrentHashMap<>();

    // Pending invites: invitee UUID → inviter's island ID
    private final Map<UUID, Integer> pendingInvites = new ConcurrentHashMap<>();

    public IslandManager(PHSkyBlock plugin, IslandRepository repo, WorldManager worldManager) {
        this.plugin = plugin;
        this.repo = repo;
        this.worldManager = worldManager;
    }

    // ── Island erstellen ──────────────────────────────────────────────────────

    public Island createIsland(Player player) {
        int distance = plugin.getConfig().getInt("island.distance", 500);
        int defSize  = plugin.getConfig().getInt("island.default-size", 80);
        int maxMem   = plugin.getConfig().getInt("island.max-members", 4);

        int index = repo.getAndIncrementIslandIndex();
        int cx = (index % 200) * distance;
        int cz = (index / 200) * distance;
        int cy = 64;

        World w = worldManager.getSkyblockWorld();
        Location center = new Location(w, cx, cy, cz);
        Location home = worldManager.generateStarterIsland(center);

        long now = System.currentTimeMillis();
        Island island = new Island(
            0, player.getUniqueId(), player.getUniqueId(), player.getName(),
            w.getName(), cx, cy, cz,
            home.getX(), home.getY(), home.getZ(), 0f, 0f,
            1L, 0L, defSize, maxMem,
            false, false, null,
            now, now
        );

        repo.insertIsland(island);
        // reload to get DB-generated ID
        repo.loadIslandByOwner(player.getUniqueId()).ifPresent(loaded -> {
            islandByOwner.put(player.getUniqueId(), loaded);
            islandById.put(loaded.getId(), loaded);
        });

        return islandByOwner.get(player.getUniqueId());
    }

    // ── Insel laden / aus Cache holen ─────────────────────────────────────────

    public Island getIslandByOwner(UUID ownerUuid) {
        return islandByOwner.computeIfAbsent(ownerUuid, uuid ->
            repo.loadIslandByOwner(uuid).map(island -> {
                islandById.put(island.getId(), island);
                return island;
            }).orElse(null)
        );
    }

    public Island getIslandById(int id) {
        return islandById.computeIfAbsent(id, i ->
            repo.loadIslandById(i).map(island -> {
                islandByOwner.put(island.getOwnerUuid(), island);
                return island;
            }).orElse(null)
        );
    }

    /** Findet die Insel eines Spielers (auch als Mitglied). */
    public Island getIslandOfPlayer(UUID playerUuid) {
        // Zuerst als Owner prüfen
        Island owned = getIslandByOwner(playerUuid);
        if (owned != null) return owned;

        // Als Mitglied prüfen via SkyPlayer
        SkyPlayer sp = plugin.getPlayerManager().getPlayer(playerUuid);
        if (sp != null && sp.getIslandId() != null) {
            return getIslandById(sp.getIslandId());
        }
        return null;
    }

    public boolean hasIsland(UUID uuid) {
        return getIslandOfPlayer(uuid) != null;
    }

    // ── Speichern ─────────────────────────────────────────────────────────────

    public void saveIsland(Island island) {
        island.setLastActive(System.currentTimeMillis());
        repo.updateIsland(island);
    }

    public void invalidateCache(UUID ownerUuid) {
        islandByOwner.remove(ownerUuid);
    }

    // ── Insel zurücksetzen ────────────────────────────────────────────────────

    public void resetIsland(Island island, Player owner) {
        int halfSize = island.getSize() / 2 + 5; // etwas mehr für Sicherheit
        Location center = new Location(worldManager.getSkyblockWorld(),
            island.getCenterX(), island.getCenterY(), island.getCenterZ());

        // Spieler teleportieren bevor Clear
        if (owner != null && owner.isOnline()) {
            teleportToSpawn(owner);
            owner.sendMessage(plugin.msg("island-reset-start"));
        }
        // Alle Inselmitglieder die auf der Insel sind, zum Spawn schieben
        for (Island.IslandMember m : island.getMembers()) {
            Player mp = Bukkit.getPlayer(m.uuid());
            if (mp != null && isOnIsland(mp, island)) teleportToSpawn(mp);
        }

        // Async clearen (schwere Operation)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            worldManager.clearIslandArea(center, halfSize);
            // Neu generieren auf Main Thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location home = worldManager.generateStarterIsland(center);
                island.setHomeX(home.getX());
                island.setHomeY(home.getY());
                island.setHomeZ(home.getZ());
                island.setHomeYaw(0f);
                island.setHomePitch(0f);
                saveIsland(island);
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(plugin.msg("island-reset-done"));
                }
            });
        });
    }

    // ── Mitglieder ────────────────────────────────────────────────────────────

    public boolean invitePlayer(Island island, Player target) {
        if (island.getMembers().size() >= island.getMaxMembers()) return false;
        pendingInvites.put(target.getUniqueId(), island.getId());
        // Einladung läuft 60 Sekunden ab
        Bukkit.getScheduler().runTaskLater(plugin,
            () -> pendingInvites.remove(target.getUniqueId()), 1200L);
        return true;
    }

    public boolean acceptInvite(Player player) {
        Integer islandId = pendingInvites.remove(player.getUniqueId());
        if (islandId == null) return false;

        Island island = getIslandById(islandId);
        if (island == null) return false;
        if (island.getMembers().size() >= island.getMaxMembers()) return false;

        repo.insertMember(islandId, player.getUniqueId(), player.getName(), Island.MemberRole.MEMBER);
        // Cache aktualisieren
        invalidateCache(island.getOwnerUuid());

        // SkyPlayer aktualisieren
        SkyPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp != null) {
            sp.setIslandId(islandId);
            repo.setPlayerIslandId(player.getUniqueId(), islandId);
        }
        return true;
    }

    public void kickMember(Island island, UUID memberUuid) {
        repo.removeMember(island.getId(), memberUuid);
        island.getMembers().removeIf(m -> m.uuid().equals(memberUuid));

        SkyPlayer sp = plugin.getPlayerManager().getPlayer(memberUuid);
        if (sp != null) {
            sp.setIslandId(null);
            repo.setPlayerIslandId(memberUuid, null);
        }

        Player online = Bukkit.getPlayer(memberUuid);
        if (online != null && isOnIsland(online, island)) teleportToSpawn(online);
    }

    public void banPlayer(Island island, UUID targetUuid, String targetName) {
        // Aus Mitgliedern entfernen wenn nötig
        if (island.isMember(targetUuid) && !targetUuid.equals(island.getOwnerUuid())) {
            kickMember(island, targetUuid);
        }
        repo.insertBan(island.getId(), targetUuid, targetName);
    }

    public void unbanPlayer(Island island, UUID targetUuid) {
        repo.removeBan(island.getId(), targetUuid);
    }

    public boolean isBanned(Island island, UUID uuid) {
        return repo.isBanned(island.getId(), uuid);
    }

    public boolean hasPendingInvite(UUID uuid) {
        return pendingInvites.containsKey(uuid);
    }

    public Integer getPendingInviteIslandId(UUID uuid) {
        return pendingInvites.get(uuid);
    }

    // ── Insel löschen ─────────────────────────────────────────────────────────

    public void deleteIsland(Island island) {
        // Alle Mitglieder loswerden
        for (Island.IslandMember m : island.getMembers()) {
            repo.setPlayerIslandId(m.uuid(), null);
            SkyPlayer sp = plugin.getPlayerManager().getPlayer(m.uuid());
            if (sp != null) sp.setIslandId(null);
        }
        repo.setPlayerIslandId(island.getOwnerUuid(), null);
        SkyPlayer sp = plugin.getPlayerManager().getPlayer(island.getOwnerUuid());
        if (sp != null) sp.setIslandId(null);

        repo.deleteIsland(island.getId());
        islandByOwner.remove(island.getOwnerUuid());
        islandById.remove(island.getId());
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    public boolean isOnIsland(Player player, Island island) {
        Location loc = player.getLocation();
        if (!loc.getWorld().getName().equals(island.getWorld())) return false;
        return island.isWithinBorder(loc.getBlockX(), loc.getBlockZ());
    }

    public Island getIslandAtLocation(Location loc) {
        if (loc.getWorld() == null) return null;
        String worldName = loc.getWorld().getName();
        String skyWorld  = plugin.getConfig().getString("worlds.skyblock", "skyblock_world");
        if (!worldName.equals(skyWorld)) return null;

        // Naive Suche im Cache – bei großen Servern ggf. optimieren
        for (Island island : islandById.values()) {
            if (island.isWithinBorder(loc.getBlockX(), loc.getBlockZ())) return island;
        }
        return null;
    }

    public void teleportToSpawn(Player player) {
        var cfg = plugin.getConfig().getConfigurationSection("spawn");
        String worldName = cfg.getString("world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) w = worldManager.getLobbyWorld();
        if (w == null) return;
        double x = cfg.getDouble("x", 0.5);
        double y = cfg.getDouble("y", 65.0);
        double z = cfg.getDouble("z", 0.5);
        float yaw   = (float) cfg.getDouble("yaw", 0.0);
        float pitch = (float) cfg.getDouble("pitch", 0.0);
        player.teleport(new Location(w, x, y, z, yaw, pitch));
    }

    public List<Island> getTopIslands(int limit) {
        return repo.loadTopIslands(limit);
    }
}
