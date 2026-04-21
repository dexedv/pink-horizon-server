package de.pinkhorizon.minigames.bedwars;

import de.pinkhorizon.minigames.PHMinigames;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BedWarsGame {

    public enum GameState { WAITING, STARTING, RUNNING, ENDED }

    private static final int MIN_PLAYERS  = 2;
    private static final int COUNTDOWN_S  = 30;
    private static final int RESPAWN_TICKS = 100; // 5s

    private final PHMinigames                       plugin;
    private final BedWarsArenaConfig                arena;
    private final BedWarsScoreboard                 scoreboard;

    private GameState                               state       = GameState.WAITING;
    private int                                     countdown   = COUNTDOWN_S;
    private BukkitTask                              countdownTask;
    private BukkitTask                              scoreboardTask;
    private ResourceSpawner                         resourceSpawner;

    // player → team
    private final Map<UUID, BedWarsTeamColor>       playerTeams   = new HashMap<>();
    // player → saved inventory
    private final Map<UUID, ItemStack[]>            savedInventory = new HashMap<>();
    private final Map<UUID, GameMode>               savedGameMode  = new HashMap<>();
    // spectators (dead, no bed)
    private final Set<UUID>                         spectators    = new HashSet<>();
    // team → players
    private final Map<BedWarsTeamColor, Set<UUID>>  teams         = new EnumMap<>(BedWarsTeamColor.class);
    // beds alive per team
    private final Set<BedWarsTeamColor>             bedsAlive     = EnumSet.noneOf(BedWarsTeamColor.class);
    // teams still in game
    private final Set<BedWarsTeamColor>             aliveTeams    = EnumSet.noneOf(BedWarsTeamColor.class);

    public BedWarsGame(PHMinigames plugin, BedWarsArenaConfig arena) {
        this.plugin     = plugin;
        this.arena      = arena;
        this.scoreboard = new BedWarsScoreboard(this);

        for (BedWarsTeamColor c : BedWarsTeamColor.values()) {
            teams.put(c, new HashSet<>());
        }
    }

    // ── Join / Leave ──────────────────────────────────────────────────────

    public boolean addPlayer(Player player) {
        if (state != GameState.WAITING && state != GameState.STARTING) return false;
        if (isFull()) return false;

        BedWarsTeamColor team = getSmallestAvailableTeam();
        if (team == null) return false;

        savedInventory.put(player.getUniqueId(), player.getInventory().getContents().clone());
        savedGameMode.put(player.getUniqueId(), player.getGameMode());

        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        playerTeams.put(player.getUniqueId(), team);
        teams.get(team).add(player.getUniqueId());

        // Spawn-Teleport
        Location spawn = arena.getSpawnLocation(team);
        if (spawn != null) {
            player.teleport(spawn);
        } else {
            player.sendMessage("§c[Debug] Kein Spawn für Team " + team.name() + " gefunden! Wende dich an einen Admin.");
            plugin.getLogger().warning("Kein Spawn für Team " + team.name() + " in Arena " + arena.name
                    + " (world=" + arena.world + ", spawnData=" + arena.teamSpawnData.size() + ")");
        }

        broadcastGame("§7" + team.chatColor + player.getName() + " §7hat das Spiel betreten. §8("
                + getTotalPlayers() + "/" + getMaxPlayers() + ")");

        scoreboard.give(player);
        scoreboard.giveTab(player, team);

        if (state == GameState.WAITING && getTotalPlayers() >= MIN_PLAYERS) {
            startCountdown();
        } else if (state == GameState.STARTING && isFull()) {
            beginGame();
        }
        return true;
    }

    public void removePlayer(Player player, boolean restore) {
        UUID uuid = player.getUniqueId();
        BedWarsTeamColor team = playerTeams.remove(uuid);
        if (team != null) teams.get(team).remove(uuid);
        spectators.remove(uuid);

        scoreboard.remove(player);
        scoreboard.removeTab(player);

        if (restore) {
            ItemStack[] inv = savedInventory.remove(uuid);
            if (inv != null) player.getInventory().setContents(inv);
            else             player.getInventory().clear();
            GameMode gm = savedGameMode.remove(uuid);
            if (gm != null) player.setGameMode(gm);
            else             player.setGameMode(GameMode.SURVIVAL);
        }

        if (state == GameState.RUNNING) {
            checkWinCondition();
        } else if (state == GameState.WAITING || state == GameState.STARTING) {
            if (getTotalPlayers() < MIN_PLAYERS && countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
                state = GameState.WAITING;
                broadcastGame("§cZu wenig Spieler – Countdown abgebrochen.");
            }
        }
    }

    // ── Countdown ──────────────────────────────────────────────────────────

    private void startCountdown() {
        state    = GameState.STARTING;
        countdown = COUNTDOWN_S;

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (countdown <= 0) {
                beginGame();
                return;
            }
            if (countdown == 30 || countdown == 20 || countdown == 10
                    || countdown == 5 || countdown <= 3) {
                broadcastGame("§eSpiel startet in §f" + countdown + " §eSekunden!");
            }
            countdown--;
            scoreboard.updateAll();
        }, 20L, 20L);
    }

    // ── Game Start ──────────────────────────────────────────────────────────

    private void beginGame() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }

        state = GameState.RUNNING;
        bedsAlive.addAll(getActiveTeamColors());
        aliveTeams.addAll(getActiveTeamColors());

        World world = Bukkit.getWorld(arena.world);

        // Spieler teleportieren, Kits geben, Rüstung
        for (UUID uuid : getAllPlayers()) {
            Player p    = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            BedWarsTeamColor team = playerTeams.get(uuid);
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20);
            p.setFoodLevel(20);
            giveStartKit(p, team);
            Location spawn = arena.getSpawnLocation(team);
            if (spawn != null) p.teleport(spawn);
        }

        // Resource-Spawner starten
        if (world != null) {
            resourceSpawner = new ResourceSpawner(plugin, arena, world);
            resourceSpawner.start();
        }

        // Scoreboard-Update-Timer (alle 2s)
        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, scoreboard::updateAll, 20L, 40L);

        broadcastGame("§a§lSpiel gestartet! §rVerstöre die Betten deiner Gegner!");
        broadcastGame("§7Teams: " + getActiveTeamColors().stream()
                .map(c -> c.chatColor + c.displayName).reduce((a, b) -> a + "§7, " + b).orElse(""));
    }

    // ── Death Handling ──────────────────────────────────────────────────────

    /**
     * Wird vom BedWarsListener bei Tod aufgerufen.
     * @return true wenn Spieler eliminiert (kein Bett mehr)
     */
    public boolean handleDeath(Player victim, Player killer) {
        if (state != GameState.RUNNING) return false;
        UUID uuid = victim.getUniqueId();
        BedWarsTeamColor team = playerTeams.get(uuid);
        if (team == null) return false;

        plugin.getStatsManager().incrementStat(uuid, "deaths");
        if (killer != null) {
            plugin.getStatsManager().incrementStat(killer.getUniqueId(), "finals");
        }

        victim.setHealth(20);
        victim.getInventory().clear();

        if (bedsAlive.contains(team)) {
            // Bett noch da → Respawn nach 5s
            victim.setGameMode(GameMode.SPECTATOR);
            spectators.add(uuid);
            broadcastGame(team.chatColor + victim.getName() + " §ewird respawnt...");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (playerTeams.containsKey(uuid)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) return;
                    spectators.remove(uuid);
                    p.setGameMode(GameMode.SURVIVAL);
                    p.setHealth(20);
                    p.setFoodLevel(20);
                    Location spawn = arena.getSpawnLocation(team);
                    if (spawn != null) p.teleport(spawn);
                    giveStartKit(p, team);
                    p.sendMessage("§aRespawnt!");
                }
            }, RESPAWN_TICKS);
            return false;
        } else {
            // Eliminiert
            victim.setGameMode(GameMode.SPECTATOR);
            spectators.add(uuid);
            broadcastGame(team.chatColor + victim.getName() + " §cwurde eliminiert!");
            checkWinCondition();
            return true;
        }
    }

    // ── Bed Destruction ──────────────────────────────────────────────────────

    /**
     * @return true wenn dieses Block eine Bett-Hälfte einer Team ist (und nicht vom gleichen Team)
     */
    public BedWarsTeamColor getBedTeamAt(int x, int y, int z, String worldName) {
        if (!arena.world.equals(worldName)) return null;
        for (Map.Entry<BedWarsTeamColor, int[]> e : arena.bedBlocks.entrySet()) {
            int[] bx = e.getValue();
            if (bx[0] == x && bx[1] == y && bx[2] == z) return e.getKey();
        }
        return null;
    }

    public void destroyBed(BedWarsTeamColor team, Player breaker) {
        bedsAlive.remove(team);
        broadcastGame("§c§l" + team.chatColor + team.displayName.replace(team.chatColor, "")
                + " §c§lBett wurde zerstört"
                + (breaker != null ? " §7von §f" + breaker.getName() : "") + "§c§l!");
        if (breaker != null)
            plugin.getStatsManager().incrementStat(breaker.getUniqueId(), "beds_broken");

        // Spieler des Teams informieren
        for (UUID uuid : teams.get(team)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage("§c§l✗ Dein Bett wurde zerstört! Du kannst nicht mehr respawnen!");
        }

        // Sofortiger Eliminierungs-Check: Alle dieses Teams tot?
        boolean anyAlive = teams.get(team).stream()
                .anyMatch(u -> !spectators.contains(u) && Bukkit.getPlayer(u) != null);
        if (!anyAlive) {
            aliveTeams.remove(team);
            broadcastGame("§7Team " + team.chatColor + team.displayName + " §7wurde eliminiert!");
            checkWinCondition();
        }
        scoreboard.updateAll();
    }

    // ── Win Condition ──────────────────────────────────────────────────────

    private void checkWinCondition() {
        // Lebende Teams ermitteln: Teams mit mind. 1 aktiven Spieler (nicht Spectator)
        Set<BedWarsTeamColor> active = new HashSet<>();
        for (BedWarsTeamColor color : aliveTeams) {
            boolean any = teams.get(color).stream()
                    .anyMatch(u -> !spectators.contains(u) && Bukkit.getPlayer(u) != null);
            if (any) active.add(color);
        }
        aliveTeams.retainAll(active);

        if (aliveTeams.size() == 1) {
            BedWarsTeamColor winner = aliveTeams.iterator().next();
            endGame(winner);
        } else if (aliveTeams.isEmpty()) {
            endGame(null);
        }
    }

    private void endGame(BedWarsTeamColor winnerTeam) {
        state = GameState.ENDED;
        if (resourceSpawner != null) resourceSpawner.stop();
        if (scoreboardTask  != null) scoreboardTask.cancel();

        if (winnerTeam != null) {
            broadcastGame("§6§l★ " + winnerTeam.chatColor + winnerTeam.displayName + " §6§lhat gewonnen! ★");
            for (UUID uuid : teams.get(winnerTeam)) {
                plugin.getStatsManager().incrementStat(uuid, "wins");
            }
        } else {
            broadcastGame("§7Das Spiel endet unentschieden!");
        }

        // Verlierer
        for (BedWarsTeamColor c : BedWarsTeamColor.values()) {
            if (c == winnerTeam) continue;
            for (UUID uuid : teams.get(c)) {
                if (!teams.getOrDefault(winnerTeam, Set.of()).contains(uuid))
                    plugin.getStatsManager().incrementStat(uuid, "losses");
            }
        }

        // Alle nach 5s wiederherstellen
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : new HashSet<>(playerTeams.keySet())) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) removePlayer(p, true);
            }
            plugin.getArenaManager().gameEnded(this);
        }, 100L);
    }

    // ── Kits ──────────────────────────────────────────────────────────────

    private void giveStartKit(Player player, BedWarsTeamColor team) {
        player.getInventory().clear();
        // Wolle (16x Team-Farbe)
        player.getInventory().addItem(new ItemStack(team.woolMaterial, 16));
        // Holzschwert
        player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
        // Bretter
        player.getInventory().addItem(new ItemStack(Material.OAK_PLANKS, 8));
        // Essen
        player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
        // Lederrüstung in Teamfarbe
        giveColoredArmor(player, team);
    }

    private void giveColoredArmor(Player player, BedWarsTeamColor team) {
        ItemStack helmet  = new ItemStack(Material.LEATHER_HELMET);
        ItemStack chest   = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack legs    = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots   = new ItemStack(Material.LEATHER_BOOTS);

        for (ItemStack piece : List.of(helmet, chest, legs, boots)) {
            LeatherArmorMeta meta = (LeatherArmorMeta) piece.getItemMeta();
            meta.setColor(team.armorColor);
            piece.setItemMeta(meta);
        }
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private BedWarsTeamColor getSmallestAvailableTeam() {
        BedWarsTeamColor smallest = null;
        int minSize = Integer.MAX_VALUE;
        int idx = 0;
        for (BedWarsTeamColor c : BedWarsTeamColor.values()) {
            if (idx >= arena.maxTeams) break;
            idx++;
            int sz = teams.get(c).size();
            if (sz < arena.teamSize && sz < minSize) {
                minSize  = sz;
                smallest = c;
            }
        }
        return smallest;
    }

    private Set<BedWarsTeamColor> getActiveTeamColors() {
        Set<BedWarsTeamColor> active = EnumSet.noneOf(BedWarsTeamColor.class);
        BedWarsTeamColor[] all = BedWarsTeamColor.values();
        for (int i = 0; i < arena.maxTeams && i < all.length; i++) {
            if (!teams.get(all[i]).isEmpty()) active.add(all[i]);
        }
        return active;
    }

    private void broadcastGame(String msg) {
        for (UUID uuid : getAllPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
    }

    private boolean isFull() {
        return getTotalPlayers() >= getMaxPlayers();
    }

    private int getMaxPlayers() {
        return arena.maxTeams * arena.teamSize;
    }

    private int getTotalPlayers() {
        return playerTeams.size();
    }

    // ── Public Accessors (für Scoreboard / Listener) ────────────────────

    public GameState        getState()                                      { return state; }
    public int              getCountdown()                                   { return countdown; }
    public BedWarsArenaConfig getArena()                                    { return arena; }
    public BedWarsTeamColor getTeamOf(UUID uuid)                           { return playerTeams.get(uuid); }
    public boolean          isInGame(UUID uuid)                             { return playerTeams.containsKey(uuid); }
    public boolean          isSpectator(UUID uuid)                          { return spectators.contains(uuid); }
    public boolean          isBedAlive(BedWarsTeamColor team)              { return bedsAlive.contains(team); }
    public boolean          isTeamAlive(BedWarsTeamColor team)             { return aliveTeams.contains(team); }
    public int              getTeamSize(BedWarsTeamColor team)             { return (int) teams.get(team).stream().filter(u -> !spectators.contains(u)).count(); }
    public Set<UUID>        getAllPlayers()                                  { return Collections.unmodifiableSet(playerTeams.keySet()); }
    public Set<UUID>        getSpectators()                                 { return Collections.unmodifiableSet(spectators); }
    public BedWarsScoreboard getScoreboard()                               { return scoreboard; }
    public Map<BedWarsTeamColor, Set<UUID>> getTeams()                    { return Collections.unmodifiableMap(teams); }
}
