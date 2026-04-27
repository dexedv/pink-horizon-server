package de.pinkhorizon.dungeons.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.pinkhorizon.dungeons.DungeonType;
import de.pinkhorizon.dungeons.PHDungeons;
import de.pinkhorizon.dungeons.data.DungeonInstance;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet Dungeon-Instanzen, Parties und Belohnungen.
 */
public class DungeonManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHDungeons plugin;
    private final HikariDataSource ds;
    private final NamespacedKey CARD_KEY;

    // instanceId → DungeonInstance
    private final Map<String, DungeonInstance> instances = new ConcurrentHashMap<>();
    // playerUuid → instanceId
    private final Map<UUID, String> playerInstance = new ConcurrentHashMap<>();
    // playerUuid → pending invite (instanceId)
    private final Map<UUID, String> pendingInvites = new ConcurrentHashMap<>();

    public DungeonManager(PHDungeons plugin) {
        this.plugin   = plugin;
        this.CARD_KEY = new NamespacedKey(plugin, "dungeon_card");
        this.ds       = initPool();
        createTable();
        startInstanceWatcher();
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
        hc.setPoolName("PHDungeons-Pool");
        return new HikariDataSource(hc);
    }

    private void createTable() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_dungeon_runs (
                    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid      VARCHAR(36)  NOT NULL,
                    dungeon_id       VARCHAR(64)  NOT NULL,
                    tier             INT          NOT NULL,
                    duration_seconds INT,
                    rank             CHAR(1),
                    completed_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Dungeons-Tabelle Fehler: " + e.getMessage());
        }
    }

    // ── Instanz erstellen ─────────────────────────────────────────────────────

    public void startDungeon(Player leader, DungeonType type) {
        if (playerInstance.containsKey(leader.getUniqueId())) {
            leader.sendMessage(MM.deserialize("<red>Du bist bereits in einem Dungeon!"));
            return;
        }

        int maxInst = plugin.getConfig().getInt("max-instances", 10);
        if (instances.size() >= maxInst) {
            leader.sendMessage(MM.deserialize("<red>Maximale Instanzen-Anzahl erreicht. Bitte warte kurz."));
            return;
        }

        // Prüfe Dungeon-Karte im Inventar
        if (!consumeDungeonCard(leader, type)) {
            leader.sendMessage(MM.deserialize("<red>Du brauchst eine <white>" + type.displayName
                + "-Karte <red>um diesen Dungeon zu betreten!"));
            leader.sendMessage(MM.deserialize("<gray>Crafte sie aus: " + String.join(", ", type.cardRecipe)));
            return;
        }

        String instanceId = UUID.randomUUID().toString().substring(0, 8);
        List<UUID> members = new ArrayList<>();
        members.add(leader.getUniqueId());

        // Temporäre Welt erstellen
        String worldName = plugin.getConfig().getString("dungeon-world-prefix", "dungeon_instance_") + instanceId;
        World dungeonWorld = createDungeonWorld(worldName, type);

        DungeonInstance instance = new DungeonInstance(
            instanceId, type, leader.getUniqueId(), members, dungeonWorld,
            System.currentTimeMillis(), false
        );

        instances.put(instanceId, instance);
        playerInstance.put(leader.getUniqueId(), instanceId);

        // Spieler teleportieren
        Location spawn = dungeonWorld.getSpawnLocation();
        leader.teleport(spawn);

        leader.sendMessage(MM.deserialize("<gold>══ " + type.displayName + " ══"));
        leader.sendMessage(MM.deserialize("<gray>Dungeon gestartet! Tier: <white>" + type.tier));
        leader.sendMessage(MM.deserialize("<gray>Zeitlimit: <white>" + (type.timeLimitSeconds / 60) + " Minuten"));
        leader.sendMessage(MM.deserialize("<gray>Nutze <white>/dungeon invite <Spieler> <gray>um Mitspieler einzuladen."));
    }

    private World createDungeonWorld(String worldName, DungeonType type) {
        WorldCreator creator = new WorldCreator(worldName)
            .environment(type.tier >= 4 ? World.Environment.THE_END : World.Environment.NORMAL)
            .generateStructures(false)
            .type(WorldType.FLAT);

        // Void-Generator für saubere Instanz
        creator.generator("VoidGenerator");

        World world = creator.createWorld();
        if (world == null) {
            // Fallback: Normale Flat-Welt
            world = new WorldCreator(worldName).type(WorldType.FLAT).createWorld();
        }
        return world;
    }

    // ── Party-System ──────────────────────────────────────────────────────────

    public void invitePlayer(Player inviter, Player target) {
        String instanceId = playerInstance.get(inviter.getUniqueId());
        if (instanceId == null) {
            inviter.sendMessage(MM.deserialize("<red>Du bist in keinem Dungeon!"));
            return;
        }

        DungeonInstance instance = instances.get(instanceId);
        if (!instance.leaderId().equals(inviter.getUniqueId())) {
            inviter.sendMessage(MM.deserialize("<red>Nur der Party-Leader kann einladen!"));
            return;
        }

        if (instance.members().size() >= instance.type().maxPlayers) {
            inviter.sendMessage(MM.deserialize("<red>Die Party ist voll! (" + instance.type().maxPlayers + " Spieler max.)"));
            return;
        }

        if (playerInstance.containsKey(target.getUniqueId())) {
            inviter.sendMessage(MM.deserialize("<red>" + target.getName() + " ist bereits in einem Dungeon!"));
            return;
        }

        pendingInvites.put(target.getUniqueId(), instanceId);
        inviter.sendMessage(MM.deserialize("<green>Einladung an " + target.getName() + " gesendet."));
        target.sendMessage(MM.deserialize(
            "<gold>[Dungeon-Einladung] <white>" + inviter.getName()
            + " <gray>lädt dich zum <white>" + instance.type().displayName + " <gray>ein!"));
        target.sendMessage(MM.deserialize("<gray>Nutze <white>/dungeon accept <gray>oder <white>/dungeon decline"));

        // Invite läuft nach 60s ab
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingInvites.remove(target.getUniqueId(), instanceId)) {
                target.sendMessage(MM.deserialize("<gray>Dungeon-Einladung abgelaufen."));
            }
        }, 1200L);
    }

    public void acceptInvite(Player player) {
        String instanceId = pendingInvites.remove(player.getUniqueId());
        if (instanceId == null) {
            player.sendMessage(MM.deserialize("<red>Du hast keine ausstehende Dungeon-Einladung!"));
            return;
        }

        DungeonInstance instance = instances.get(instanceId);
        if (instance == null) {
            player.sendMessage(MM.deserialize("<red>Die Dungeon-Instanz existiert nicht mehr."));
            return;
        }

        instance.members().add(player.getUniqueId());
        playerInstance.put(player.getUniqueId(), instanceId);

        Location spawn = instance.world().getSpawnLocation();
        player.teleport(spawn);
        player.sendMessage(MM.deserialize("<green>Du bist dem Dungeon beigetreten: <white>" + instance.type().displayName));

        // Alle Mitglieder benachrichtigen
        for (UUID memberUuid : instance.members()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && !member.equals(player)) {
                member.sendMessage(MM.deserialize("<green>" + player.getName() + " ist beigetreten!"));
            }
        }
    }

    public void leaveDungeon(Player player) {
        String instanceId = playerInstance.remove(player.getUniqueId());
        if (instanceId == null) {
            player.sendMessage(MM.deserialize("<red>Du bist in keinem Dungeon!"));
            return;
        }

        DungeonInstance instance = instances.get(instanceId);
        if (instance != null) {
            instance.members().remove(player.getUniqueId());
        }

        returnToOverworld(player);
        player.sendMessage(MM.deserialize("<gray>Du hast den Dungeon verlassen."));

        // Wenn alle weg → Instanz aufräumen
        if (instance != null && instance.members().isEmpty()) {
            cleanupInstance(instanceId);
        }
    }

    // ── Dungeon-Abschluss ─────────────────────────────────────────────────────

    public void completeInstance(String instanceId) {
        DungeonInstance instance = instances.get(instanceId);
        if (instance == null) return;

        int elapsed = instance.elapsedSeconds();
        String rank = instance.type().getRank(elapsed);

        for (UUID memberUuid : instance.members()) {
            Player member = Bukkit.getPlayer(memberUuid);
            saveRun(memberUuid, instance.type(), elapsed, rank);

            if (member != null) {
                member.sendMessage(MM.deserialize("<gold>══ Dungeon Abgeschlossen! ══"));
                member.sendMessage(MM.deserialize("<gray>Zeit: <white>" + formatTime(elapsed)));
                member.sendMessage(MM.deserialize("<gray>Rang: " + getRankColor(rank) + rank));

                // Belohnungen
                giveRewards(member, instance.type(), rank);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    returnToOverworld(member);
                    playerInstance.remove(member.getUniqueId());
                }, 100L);
            }
        }

        cleanupInstance(instanceId);
    }

    private void giveRewards(Player player, DungeonType type, String rank) {
        // Basis-Belohnungen je Tier
        int coins = type.tier * 5000;
        if (rank.equals("S")) coins *= 3;
        else if (rank.equals("A")) coins *= 2;

        // Vereinfacht: Item-Drops
        ItemStack reward = new ItemStack(type.guiIcon, type.tier);
        player.getInventory().addItem(reward);
        player.sendMessage(MM.deserialize("<yellow>Belohnung: " + coins + " Coins + Item-Drops!"));
        // TODO: PHCore Economy integration
    }

    // ── Instanz-Verwaltung ────────────────────────────────────────────────────

    private void startInstanceWatcher() {
        new BukkitRunnable() {
            @Override public void run() {
                List<String> expired = new ArrayList<>();
                instances.forEach((id, inst) -> {
                    if (inst.isExpired()) expired.add(id);
                });
                expired.forEach(id -> {
                    DungeonInstance inst = instances.get(id);
                    if (inst != null) {
                        // Alle Spieler rauswerfen
                        for (UUID uuid : new ArrayList<>(inst.members())) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null) {
                                p.sendMessage(MM.deserialize("<red>Zeitlimit überschritten! Dungeon beendet."));
                                returnToOverworld(p);
                                playerInstance.remove(uuid);
                            }
                        }
                    }
                    cleanupInstance(id);
                });
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    private void cleanupInstance(String instanceId) {
        DungeonInstance instance = instances.remove(instanceId);
        if (instance == null) return;

        World w = instance.world();
        if (w == null) return;

        // Welt entladen und löschen
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.unloadWorld(w, false);
            // Welt-Ordner löschen
            deleteWorldFolder(w.getWorldFolder());
        });
    }

    private void deleteWorldFolder(java.io.File folder) {
        if (!folder.exists()) return;
        for (java.io.File file : Objects.requireNonNull(folder.listFiles())) {
            if (file.isDirectory()) deleteWorldFolder(file);
            else file.delete();
        }
        folder.delete();
    }

    private void returnToOverworld(Player player) {
        World overworld = Bukkit.getWorlds().stream()
            .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
            .filter(w -> !w.getName().startsWith(plugin.getConfig().getString("dungeon-world-prefix", "dungeon_")))
            .findFirst()
            .orElse(Bukkit.getWorlds().get(0));
        player.teleport(overworld.getSpawnLocation());
    }

    // ── DB-Speichern ──────────────────────────────────────────────────────────

    private void saveRun(UUID uuid, DungeonType type, int seconds, String rank) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sky_dungeon_runs (player_uuid, dungeon_id, tier, duration_seconds, rank) VALUES(?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type.name());
                ps.setInt   (3, type.tier);
                ps.setInt   (4, seconds);
                ps.setString(5, rank);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Dungeon-Run speichern fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    // ── Status anzeigen ───────────────────────────────────────────────────────

    public void showStatus(Player player) {
        String instanceId = playerInstance.get(player.getUniqueId());
        if (instanceId == null) {
            player.sendMessage(MM.deserialize("<gray>Du bist in keinem Dungeon."));
            return;
        }
        DungeonInstance inst = instances.get(instanceId);
        if (inst == null) return;

        int elapsed = inst.elapsedSeconds();
        int remaining = inst.type().timeLimitSeconds - elapsed;
        player.sendMessage(MM.deserialize("<gold>══ " + inst.type().displayName + " ══"));
        player.sendMessage(MM.deserialize("<gray>Zeit: <white>" + formatTime(elapsed)
            + " <gray>| Verbleibend: <white>" + formatTime(Math.max(0, remaining))));
        player.sendMessage(MM.deserialize("<gray>Spieler: <white>" + inst.members().size() + "/" + inst.type().maxPlayers));
    }

    // ── Dungeon-Liste ─────────────────────────────────────────────────────────

    public void showList(Player player) {
        new de.pinkhorizon.dungeons.gui.DungeonBrowserGui(plugin, player).open(player);
    }

    // ── Dungeon-Karten ────────────────────────────────────────────────────────

    public ItemStack createDungeonCard(DungeonType type) {
        ItemStack item = new ItemStack(type.guiIcon);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold><bold>" + type.displayName + "-Karte"));
        meta.lore(List.of(
            MM.deserialize("<gray>" + type.description),
            MM.deserialize("<dark_gray>Tier: <white>" + type.tier
                + " <dark_gray>| Spieler: <white>" + type.minPlayers + "-" + type.maxPlayers),
            MM.deserialize("<dark_gray>Zeit: <white>" + (type.timeLimitSeconds / 60) + " min"),
            MM.deserialize("<yellow>Nutze /dungeon enter " + type.name().toLowerCase())
        ));
        meta.getPersistentDataContainer().set(CARD_KEY, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private boolean consumeDungeonCard(Player player, DungeonType type) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || !item.hasItemMeta()) continue;
            String val = item.getItemMeta().getPersistentDataContainer()
                .get(CARD_KEY, PersistentDataType.STRING);
            if (type.name().equals(val)) {
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) inv.setItem(i, null);
                return true;
            }
        }
        return false;
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private String getRankColor(String rank) {
        return switch (rank) {
            case "S" -> "<gold>";
            case "A" -> "<green>";
            case "B" -> "<yellow>";
            default  -> "<gray>";
        };
    }

    public boolean isInDungeon(Player player) {
        return playerInstance.containsKey(player.getUniqueId());
    }

    public String getInstanceId(Player player) {
        return playerInstance.get(player.getUniqueId());
    }

    public void close() {
        instances.keySet().forEach(this::cleanupInstance);
        if (ds != null && !ds.isClosed()) ds.close();
    }
}
