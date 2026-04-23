package de.pinkhorizon.smash.npc;

import de.pinkhorizon.smash.PHSmash;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class NpcManager implements Listener {

    private static final String KEY_DOWN    = "leveldown";
    private static final String KEY_UPGRADE = "upgrade";
    private static final double LOOK_RANGE  = 8.0; // Blöcke Reichweite

    private final PHSmash   plugin;
    private UUID            levelDownUuid;
    private UUID            upgradeUuid;
    private BukkitTask      lookTask;

    public NpcManager(PHSmash plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTask(plugin, () -> {
            restoreNpcs();
            startLookTask();
        });
    }

    // ── Startup ────────────────────────────────────────────────────────────

    private void restoreNpcs() {
        levelDownUuid = findOrRespawn(KEY_DOWN);
        upgradeUuid   = findOrRespawn(KEY_UPGRADE);
    }

    /** Sucht bestehende Entität per UUID; fehlt sie, wird sie neu gespawnt. */
    private UUID findOrRespawn(String key) {
        String saved = plugin.getConfig().getString("npcs." + key + ".uuid");
        if (saved != null) {
            try {
                UUID uuid = UUID.fromString(saved);
                for (World w : Bukkit.getWorlds()) {
                    Entity e = w.getEntity(uuid);
                    if (e instanceof Villager) return uuid;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // Nicht gefunden → neu spawnen falls Location konfiguriert
        Location loc = loadLocation(key);
        if (loc == null) return null;
        return doSpawn(key, loc);
    }

    // ── Öffentliche API ────────────────────────────────────────────────────

    /**
     * NPC an aktueller Admin-Position setzen (Admin-Befehl).
     * @param key "leveldown" oder "upgrade"
     */
    public void setNpc(Player admin, String key) {
        Location loc = admin.getLocation();

        // Alte Entität entfernen
        String oldStr = plugin.getConfig().getString("npcs." + key + ".uuid");
        if (oldStr != null) {
            try {
                UUID old = UUID.fromString(oldStr);
                for (World w : Bukkit.getWorlds()) {
                    Entity e = w.getEntity(old);
                    if (e != null) { e.remove(); break; }
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // Location in Config speichern
        String path = "npcs." + key;
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x",     loc.getX());
        plugin.getConfig().set(path + ".y",     loc.getY());
        plugin.getConfig().set(path + ".z",     loc.getZ());
        plugin.getConfig().set(path + ".yaw",   (double) loc.getYaw());
        plugin.saveConfig();

        UUID uuid = doSpawn(key, loc);
        if (KEY_DOWN.equals(key))    levelDownUuid = uuid;
        else                          upgradeUuid   = uuid;

        String label = KEY_DOWN.equals(key) ? "Level senken" : "Upgrade";
        admin.sendMessage("§a✔ §7NPC §e" + label + " §7gesetzt.");
    }

    // ── Interaction ────────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        UUID clicked = event.getRightClicked().getUniqueId();
        Player player = event.getPlayer();

        if (clicked.equals(levelDownUuid)) {
            event.setCancelled(true);
            handleLevelDown(player);
        } else if (clicked.equals(upgradeUuid)) {
            event.setCancelled(true);
            plugin.getUpgradeGui().open(player);
        }
    }

    private void handleLevelDown(Player player) {
        int current = plugin.getPlayerDataManager().getPersonalBossLevel(player.getUniqueId());
        if (current <= 1) {
            player.sendMessage("§cDu bist bereits auf Boss Level 1!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        int next = current - 1;
        plugin.getPlayerDataManager().setPersonalBossLevel(player.getUniqueId(), next);
        player.sendMessage("§a✔ §7Boss Level auf §c" + next + " §7gesenkt. Tritt mit §e/stb join §7an!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.7f);
    }

    // ── Look-Task ──────────────────────────────────────────────────────────

    private void startLookTask() {
        lookTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            rotateNpc(levelDownUuid);
            rotateNpc(upgradeUuid);
        }, 5L, 10L); // alle 0.5 Sekunden
    }

    /** Dreht den NPC zum nächsten Spieler innerhalb LOOK_RANGE Blöcke. */
    private void rotateNpc(UUID uuid) {
        if (uuid == null) return;
        Entity npc = null;
        for (World w : Bukkit.getWorlds()) {
            Entity e = w.getEntity(uuid);
            if (e != null) { npc = e; break; }
        }
        if (npc == null || !npc.isValid()) return;

        Player nearest = null;
        double minDist = LOOK_RANGE * LOOK_RANGE;
        for (Player p : npc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(npc.getLocation());
            if (d < minDist) { minDist = d; nearest = p; }
        }
        if (nearest == null) return;

        // Richtungsvektor NPC-Augen → Spieler-Augen
        Location from = npc.getLocation().add(0, npc.getHeight() * 0.85, 0);
        Location to   = nearest.getEyeLocation();
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

        Location npcLoc = npc.getLocation();
        npcLoc.setYaw(yaw);
        npcLoc.setPitch(Math.max(-30f, Math.min(30f, pitch))); // Pitch begrenzen
        npc.teleport(npcLoc);
    }

    public void stop() {
        if (lookTask != null) lookTask.cancel();
    }

    // ── Intern ─────────────────────────────────────────────────────────────

    private UUID doSpawn(String key, Location loc) {
        boolean isDown = KEY_DOWN.equals(key);

        Villager npc = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setPersistent(true);
        npc.setRemoveWhenFarAway(false);
        npc.setGlowing(true);
        npc.setCustomNameVisible(true);
        npc.setCustomName(isDown ? "§c§l▼ Level senken" : "§6§l⬆ Upgrades");
        npc.setProfession(isDown ? Villager.Profession.CLERIC : Villager.Profession.TOOLSMITH);
        npc.setVillagerLevel(5);

        plugin.getConfig().set("npcs." + key + ".uuid", npc.getUniqueId().toString());
        plugin.saveConfig();
        return npc.getUniqueId();
    }

    private Location loadLocation(String key) {
        String path      = "npcs." + key;
        String worldName = plugin.getConfig().getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
            plugin.getConfig().getDouble(path + ".x"),
            plugin.getConfig().getDouble(path + ".y"),
            plugin.getConfig().getDouble(path + ".z"),
            (float) plugin.getConfig().getDouble(path + ".yaw"), 0f);
    }
}
