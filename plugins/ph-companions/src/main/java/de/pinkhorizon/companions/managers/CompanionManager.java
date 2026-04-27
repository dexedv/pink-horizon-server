package de.pinkhorizon.companions.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.companions.CompanionType;
import de.pinkhorizon.companions.PHCompanions;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet alle Begleiter: Spawnen, Hunger, Dialoge, Arbeits-Ticks.
 */
public class CompanionManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Food-Items die Begleiter akzeptieren (beliebige Nahrung)
    private static final Set<Material> FOOD_ITEMS = Set.of(
        Material.BREAD, Material.CARROT, Material.POTATO, Material.BAKED_POTATO,
        Material.COOKED_BEEF, Material.COOKED_CHICKEN, Material.COOKED_PORKCHOP,
        Material.APPLE, Material.GOLDEN_APPLE, Material.MELON_SLICE,
        Material.COOKIE, Material.CAKE, Material.PUMPKIN_PIE
    );

    private final PHCompanions plugin;
    private final HikariDataSource ds;
    private final NamespacedKey COMPANION_KEY;
    private final NamespacedKey COMPANION_OWNER_KEY;

    // player-uuid → list of active companion entities
    private final Map<UUID, List<Entity>> activeEntities   = new ConcurrentHashMap<>();
    // entity-uuid → companion type
    private final Map<UUID, CompanionType> entityTypeMap   = new ConcurrentHashMap<>();
    // entity-uuid → owner uuid
    private final Map<UUID, UUID> entityOwnerMap           = new ConcurrentHashMap<>();
    // player-uuid → companion type → hunger ticks remaining (0 = sleeping)
    private final Map<UUID, Map<CompanionType, Integer>> hungerMap = new ConcurrentHashMap<>();
    // player-uuid → companion type → viktor daily work ticks used
    private final Map<UUID, Integer> viktorDailyTicks      = new ConcurrentHashMap<>();

    private static final int MAX_HUNGER      = 72_000; // 1h = 72000 ticks
    private static final int HUNGER_DRAIN    = 200;    // every 200 ticks -1 hunger
    private static final int VIKTOR_MAX_TICKS = 288_000; // 4h/day

    private BukkitTask mainTask;

    public CompanionManager(PHCompanions plugin) {
        this.plugin            = plugin;
        this.COMPANION_KEY     = new NamespacedKey(plugin, "companion_type");
        this.COMPANION_OWNER_KEY = new NamespacedKey(plugin, "companion_owner");
        this.ds                = initPool();
        createTable();
        loadAll();
        startTasks();
    }

    // ── DB ───────────────────────────────────────────────────────────────────

    private HikariDataSource initPool() {
        var cfg = plugin.getConfig().getConfigurationSection("database");
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://"
            + cfg.getString("host", "ph-mysql") + ":" + cfg.getInt("port", 3306)
            + "/" + cfg.getString("name", "ph_skyblock") + "?useSSL=false&serverTimezone=UTC");
        hc.setUsername(cfg.getString("user", "ph_user"));
        hc.setPassword(cfg.getString("password", ""));
        hc.setMaximumPoolSize(cfg.getInt("pool-size", 4));
        hc.setPoolName("PHCompanions-Pool");
        return new HikariDataSource(hc);
    }

    private void createTable() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_companions (
                    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid   VARCHAR(36) NOT NULL,
                    companion_type VARCHAR(32) NOT NULL,
                    level         INT          DEFAULT 1,
                    xp            BIGINT       DEFAULT 0,
                    hunger        INT          DEFAULT 72000,
                    active        TINYINT      DEFAULT 0,
                    UNIQUE KEY one_type (player_uuid, companion_type)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Companions-Tabelle Fehler: " + e.getMessage());
        }
    }

    private void loadAll() {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT player_uuid, companion_type, hunger FROM sky_companions WHERE active=1")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID owner = UUID.fromString(rs.getString("player_uuid"));
                    try {
                        CompanionType type = CompanionType.valueOf(rs.getString("companion_type"));
                        int hunger = rs.getInt("hunger");
                        hungerMap.computeIfAbsent(owner, k -> new ConcurrentHashMap<>())
                                 .put(type, hunger);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Companions laden fehlgeschlagen: " + e.getMessage());
        }
    }

    // ── Spawn / Dismiss ──────────────────────────────────────────────────────

    public void summonCompanion(Player player, CompanionType type) {
        UUID uuid = player.getUniqueId();
        List<Entity> current = activeEntities.getOrDefault(uuid, new ArrayList<>());

        // Max 2 aktive Begleiter
        if (current.size() >= 2) {
            player.sendMessage(MM.deserialize("<red>Du kannst maximal 2 Begleiter gleichzeitig beschwören!"));
            return;
        }

        // Bereits beschworen?
        boolean alreadyActive = current.stream()
            .anyMatch(e -> entityTypeMap.get(e.getUniqueId()) == type);
        if (alreadyActive) {
            player.sendMessage(MM.deserialize("<red>" + type.displayName + " ist bereits aktiv!"));
            return;
        }

        // Prüfe ob Spieler diesen Begleiter besitzt
        if (!ownsCompanion(uuid, type)) {
            player.sendMessage(MM.deserialize("<red>Du besitzt " + type.displayName + " nicht!"));
            return;
        }

        Location spawnLoc = player.getLocation().add(1.5, 0, 0);
        Entity entity = spawnCompanionEntity(spawnLoc, type, player);

        current = activeEntities.computeIfAbsent(uuid, k -> new ArrayList<>());
        current.add(entity);
        entityTypeMap.put(entity.getUniqueId(), type);
        entityOwnerMap.put(entity.getUniqueId(), uuid);

        // Hunger aus DB/Cache laden
        int hunger = hungerMap.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                              .getOrDefault(type, MAX_HUNGER);
        hungerMap.get(uuid).put(type, hunger);

        setActiveInDb(uuid, type, true);
        player.sendMessage(MM.deserialize("<green>" + type.displayName + " <gray>wurde beschworen! "
            + "<yellow>Hunger: " + (hunger * 100 / MAX_HUNGER) + "%"));
    }

    private Entity spawnCompanionEntity(Location loc, CompanionType type, Player owner) {
        return loc.getWorld().spawn(loc, type.entityType.getEntityClass(), e -> {
            if (e instanceof Mob mob) {
                mob.setAI(false);
                mob.setInvulnerable(true);
                mob.setSilent(false);
                mob.setRemoveWhenFarAway(false);
            }
            if (e instanceof LivingEntity le) {
                le.customName(MM.deserialize("<gold>" + type.displayName));
                le.setCustomNameVisible(true);
            }
            e.getPersistentDataContainer().set(COMPANION_KEY, PersistentDataType.STRING, type.name());
            e.getPersistentDataContainer().set(COMPANION_OWNER_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
        });
    }

    public void dismissCompanion(Player player, CompanionType type) {
        UUID uuid = player.getUniqueId();
        List<Entity> current = activeEntities.getOrDefault(uuid, new ArrayList<>());

        Entity toRemove = null;
        for (Entity e : current) {
            if (entityTypeMap.get(e.getUniqueId()) == type) {
                toRemove = e;
                break;
            }
        }

        if (toRemove == null) {
            player.sendMessage(MM.deserialize("<red>" + type.displayName + " ist nicht aktiv!"));
            return;
        }

        removeEntity(toRemove);
        current.remove(toRemove);
        if (current.isEmpty()) activeEntities.remove(uuid);

        setActiveInDb(uuid, type, false);
        player.sendMessage(MM.deserialize("<gray>" + type.displayName + " wurde verabschiedet."));
    }

    private void removeEntity(Entity e) {
        UUID eid = e.getUniqueId();
        entityTypeMap.remove(eid);
        entityOwnerMap.remove(eid);
        e.remove();
    }

    // ── Füttern ──────────────────────────────────────────────────────────────

    public void feedCompanion(Player player, CompanionType type) {
        UUID uuid = player.getUniqueId();

        // Prüfe ob Spieler Food-Item im Haupthand hat
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType() == Material.AIR || !FOOD_ITEMS.contains(inHand.getType())) {
            player.sendMessage(MM.deserialize("<red>Halte ein Nahrungsmittel in der Hand zum Füttern!"));
            return;
        }

        Map<CompanionType, Integer> hunger = hungerMap.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        int current = hunger.getOrDefault(type, MAX_HUNGER);

        if (current >= MAX_HUNGER) {
            player.sendMessage(MM.deserialize("<yellow>" + type.displayName + " ist nicht hungrig!"));
            return;
        }

        // Item nehmen
        inHand.setAmount(inHand.getAmount() - 1);
        int restored = MAX_HUNGER / 4; // 25% pro Item
        int newHunger = Math.min(MAX_HUNGER, current + restored);
        hunger.put(type, newHunger);

        saveHunger(uuid, type, newHunger);
        player.sendMessage(MM.deserialize("<green>" + type.displayName + " gefüttert! "
            + "<yellow>Hunger: " + (newHunger * 100 / MAX_HUNGER) + "%"));
    }

    // ── Info ─────────────────────────────────────────────────────────────────

    public void showInfo(Player player, CompanionType type) {
        UUID uuid = player.getUniqueId();
        int[] data = getCompanionData(uuid, type);
        int level = data[0];
        long xp = data[1];
        int hunger = hungerMap.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                              .getOrDefault(type, 0);

        boolean active = activeEntities.getOrDefault(uuid, List.of()).stream()
            .anyMatch(e -> entityTypeMap.get(e.getUniqueId()) == type);

        player.sendMessage(MM.deserialize("<gold>══ " + type.displayName + " ══"));
        player.sendMessage(MM.deserialize("<gray>Fähigkeit: <white>" + type.ability));
        player.sendMessage(MM.deserialize("<gray>Level: <white>" + level + "/30"));
        player.sendMessage(MM.deserialize("<gray>XP: <white>" + xp));
        player.sendMessage(MM.deserialize("<gray>Hunger: <white>" + (hunger * 100 / MAX_HUNGER) + "%"
            + (hunger <= 0 ? " <red>(Schläft!)" : "")));
        player.sendMessage(MM.deserialize("<gray>Status: " + (active ? "<green>Aktiv" : "<dark_gray>Inaktiv")));
        player.sendMessage(MM.deserialize("<gray>Level-Boni: <white>" + type.levelBonuses));
    }

    // ── Begleiter-Liste ──────────────────────────────────────────────────────

    public void showList(Player player) {
        new de.pinkhorizon.companions.gui.CompanionMenuGui(plugin, player).open(player);
    }

    // ── Grant (Admin) ─────────────────────────────────────────────────────────

    public void grantCompanion(Player target, CompanionType type) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT IGNORE INTO sky_companions (player_uuid, companion_type) VALUES(?,?)")) {
            ps.setString(1, target.getUniqueId().toString());
            ps.setString(2, type.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Grant companion fehler: " + e.getMessage());
        }
    }

    // ── Tick-System ──────────────────────────────────────────────────────────

    private void startTasks() {
        // Haupt-Tick: alle 200 Ticks (~10s)
        mainTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                tickHunger();
                tickWork();
                // Dialog alle ~1 Minute (6 × 200 = 1200 ticks)
                if (tick % 6 == 0) tickDialog();
                // Mitternacht-Reset für Viktor
                if (tick % 24 == 0) resetViktorDaily();
            }
        }.runTaskTimer(plugin, 200L, 200L);

        // Auto-Save alle 5 Min
        new BukkitRunnable() {
            @Override public void run() { saveAllHunger(); }
        }.runTaskTimer(plugin, 6000L, 6000L);
    }

    private void tickHunger() {
        activeEntities.forEach((ownerUuid, entities) -> {
            Map<CompanionType, Integer> hunger = hungerMap.computeIfAbsent(ownerUuid, k -> new ConcurrentHashMap<>());
            for (Entity e : entities) {
                CompanionType type = entityTypeMap.get(e.getUniqueId());
                if (type == null) continue;
                int h = hunger.getOrDefault(type, MAX_HUNGER);
                hunger.put(type, Math.max(0, h - 1));
            }
        });
    }

    private void tickWork() {
        activeEntities.forEach((ownerUuid, entities) -> {
            Player owner = Bukkit.getPlayer(ownerUuid);
            Map<CompanionType, Integer> hunger = hungerMap.computeIfAbsent(ownerUuid, k -> new ConcurrentHashMap<>());

            for (Entity e : entities) {
                CompanionType type = entityTypeMap.get(e.getUniqueId());
                if (type == null) continue;
                int h = hunger.getOrDefault(type, 0);
                if (h <= 0) continue; // Schläft — kein Hunger = keine Arbeit

                switch (type) {
                    case FELD_FELIX  -> doFelixHarvest(e);
                    case GOLEM_GUSTAV -> doGustavTransport(e, owner);
                    case VOID_VIKTOR  -> doViktorFish(ownerUuid, owner, e);
                    default -> {} // Helga, Bert, Hanna: komplexere Logik, TODO
                }

                // XP für Arbeit
                addXp(ownerUuid, type, 5);
            }
        });
    }

    private void doFelixHarvest(Entity felix) {
        Location center = felix.getLocation();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = center.clone().add(dx, 0, dz).getBlock();
                if (isMatureCrop(block)) {
                    block.breakNaturally();
                }
            }
        }
    }

    private void doGustavTransport(Entity gustav, Player owner) {
        if (owner == null) return;
        Location loc = gustav.getLocation();
        // Findet nächste Truhe im 5-Block-Radius
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    Block b = loc.clone().add(dx, dy, dz).getBlock();
                    if (b.getState() instanceof Chest chest) {
                        // Übertrage Items aus Boden (dropped items) in die Truhe
                        // (Vereinfachte Demo-Logik)
                        return;
                    }
                }
            }
        }
    }

    private void doViktorFish(UUID ownerUuid, Player owner, Entity viktor) {
        int used = viktorDailyTicks.getOrDefault(ownerUuid, 0);
        if (used >= VIKTOR_MAX_TICKS / 200) return; // 4h / 200-tick-intervall
        viktorDailyTicks.merge(ownerUuid, 1, Integer::sum);

        // Simuliere einen Void-Angel-Fang alle ~2 Minuten (600 ticks / 200 ticks interval = 3)
        if (used % 3 == 0) {
            // Spawn ein zufälliges "Void-Item" als dropped entity neben Viktor
            ItemStack catch_ = new ItemStack(Material.INK_SAC); // Platzhalter
            viktor.getWorld().dropItemNaturally(viktor.getLocation(), catch_);
            if (owner != null) {
                owner.sendMessage(MM.deserialize("<dark_purple>Viktor hat etwas aus der Leere gefischt!"));
            }
        }
    }

    private void tickDialog() {
        Random rng = new Random();
        activeEntities.forEach((ownerUuid, entities) -> {
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner == null) return;
            for (Entity e : entities) {
                CompanionType type = entityTypeMap.get(e.getUniqueId());
                if (type == null) continue;
                // 30% Chance auf Dialog
                if (rng.nextInt(10) < 3) {
                    String line = type.dialogLines[rng.nextInt(type.dialogLines.length)];
                    owner.sendMessage(MM.deserialize("<gold>[" + type.displayName + "] <white>" + line));
                }
            }
        });
    }

    private void resetViktorDaily() {
        viktorDailyTicks.clear();
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private boolean isMatureCrop(Block block) {
        var data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.Ageable crop) {
            return crop.getAge() == crop.getMaximumAge();
        }
        return false;
    }

    private int[] getCompanionData(UUID uuid, CompanionType type) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT level, xp FROM sky_companions WHERE player_uuid=? AND companion_type=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new int[]{rs.getInt("level"), rs.getInt("xp")};
            }
        } catch (SQLException ignored) {}
        return new int[]{1, 0};
    }

    private void addXp(UUID uuid, CompanionType type, int amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE sky_companions SET xp = xp + ? WHERE player_uuid=? AND companion_type=?")) {
                ps.setInt(1, amount);
                ps.setString(2, uuid.toString());
                ps.setString(3, type.name());
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    private void setActiveInDb(UUID uuid, CompanionType type, boolean active) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE sky_companions SET active=? WHERE player_uuid=? AND companion_type=?")) {
                ps.setInt(1, active ? 1 : 0);
                ps.setString(2, uuid.toString());
                ps.setString(3, type.name());
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    private void saveHunger(UUID uuid, CompanionType type, int hunger) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE sky_companions SET hunger=? WHERE player_uuid=? AND companion_type=?")) {
                ps.setInt(1, hunger);
                ps.setString(2, uuid.toString());
                ps.setString(3, type.name());
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    private void saveAllHunger() {
        hungerMap.forEach((uuid, typeMap) ->
            typeMap.forEach((type, hunger) -> saveHunger(uuid, type, hunger)));
    }

    // ── GUI-Hilfsmethoden (public) ────────────────────────────────────────────

    public boolean ownsCompanion(UUID uuid, CompanionType type) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id FROM sky_companions WHERE player_uuid=? AND companion_type=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isActive(UUID uuid, CompanionType type) {
        return activeEntities.getOrDefault(uuid, List.of()).stream()
            .anyMatch(e -> entityTypeMap.get(e.getUniqueId()) == type);
    }

    public int getHungerPercent(UUID uuid, CompanionType type) {
        int h = hungerMap.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                         .getOrDefault(type, MAX_HUNGER);
        return h * 100 / MAX_HUNGER;
    }

    public boolean isCompanionEntity(Entity e) {
        return e.getPersistentDataContainer().has(COMPANION_KEY, PersistentDataType.STRING);
    }

    public void despawnAllForPlayer(Player player) {
        List<Entity> entities = activeEntities.remove(player.getUniqueId());
        if (entities == null) return;
        for (Entity e : entities) removeEntity(e);
    }

    public void close() {
        if (mainTask != null) mainTask.cancel();
        saveAllHunger();
        activeEntities.values().forEach(list -> list.forEach(Entity::remove));
        if (ds != null && !ds.isClosed()) ds.close();
    }
}
