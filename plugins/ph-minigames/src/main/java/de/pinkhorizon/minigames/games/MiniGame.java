package de.pinkhorizon.minigames.games;

import de.pinkhorizon.minigames.PHMinigames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MiniGame {

    private final PHMinigames plugin;
    private final String arenaId;
    private final GameType type;
    private final int maxPlayers;
    private GameState state = GameState.WAITING;

    private final List<UUID> players = new ArrayList<>();
    private final List<UUID> spectators = new ArrayList<>();
    private final Map<UUID, GameMode> previousGameModes = new HashMap<>();
    private final Map<UUID, ItemStack[]> previousInventories = new HashMap<>();

    private BukkitTask countdownTask;
    private BukkitTask gameTimerTask;
    private int countdown = 30;
    private int gameTimeSeconds = 0;
    private static final int MAX_GAME_TIME = 600; // 10 Minuten

    public MiniGame(PHMinigames plugin, String arenaId, GameType type, int maxPlayers) {
        this.plugin = plugin;
        this.arenaId = arenaId;
        this.type = type;
        this.maxPlayers = maxPlayers;
    }

    // ── Spieler-Verwaltung ──────────────────────────────────────────────

    public boolean addPlayer(UUID uuid) {
        if (players.contains(uuid) || state != GameState.WAITING) return false;
        players.add(uuid);

        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            previousGameModes.put(uuid, p.getGameMode());
            previousInventories.put(uuid, p.getInventory().getContents().clone());
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            p.sendMessage(Component.text("[" + type.getDisplayName() + "] Beigetreten! (" + players.size() + "/" + maxPlayers + ")", NamedTextColor.GREEN));
        }

        broadcast(Component.text("[" + type.getDisplayName() + "] " +
                (p != null ? p.getName() : uuid) + " ist beigetreten. (" + players.size() + "/" + maxPlayers + ")", NamedTextColor.YELLOW));

        if (players.size() >= 2 && state == GameState.WAITING) startCountdown();
        if (players.size() >= maxPlayers) skipCountdown();
        return true;
    }

    public void removePlayer(UUID uuid) {
        if (!players.remove(uuid)) return;

        restorePlayer(uuid);

        if (state == GameState.RUNNING) {
            checkWinCondition();
        } else if (state == GameState.COUNTDOWN && players.size() < 2) {
            cancelCountdown();
        }
    }

    // ── Spielablauf ─────────────────────────────────────────────────────

    private void startCountdown() {
        state = GameState.COUNTDOWN;
        countdown = 30;
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (countdown <= 0) { startGame(); return; }
            if (countdown <= 5 || countdown % 10 == 0) {
                broadcast(Component.text("Spiel startet in " + countdown + " Sekunden!", NamedTextColor.YELLOW));
            }
            countdown--;
        }, 0L, 20L);
    }

    private void skipCountdown() {
        countdown = 3;
    }

    private void cancelCountdown() {
        if (countdownTask != null) countdownTask.cancel();
        state = GameState.WAITING;
        broadcast(Component.text("Zu wenig Spieler – Countdown abgebrochen.", NamedTextColor.RED));
    }

    private void startGame() {
        if (countdownTask != null) countdownTask.cancel();
        state = GameState.RUNNING;
        gameTimeSeconds = 0;

        giveKits();
        broadcast(Component.text("=== " + type.getDisplayName() + " gestartet! Viel Erfolg! ===", NamedTextColor.GREEN));

        // Timer fuer maximale Spiellaenge
        gameTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            gameTimeSeconds++;
            if (gameTimeSeconds >= MAX_GAME_TIME) {
                broadcast(Component.text("Zeit abgelaufen! Unentschieden!", NamedTextColor.GOLD));
                endGame(null);
            }
        }, 20L, 20L);
    }

    private void giveKits() {
        players.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) return;
            switch (type) {
                case BEDWARS -> giveBedWarsKit(p);
                case SKYWARS -> giveSkyWarsKit(p);
            }
        });
    }

    private void giveBedWarsKit(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(
                new ItemStack(org.bukkit.Material.WOODEN_SWORD),
                new ItemStack(org.bukkit.Material.OAK_PLANKS, 16),
                new ItemStack(org.bukkit.Material.COOKED_BEEF, 8)
        );
    }

    private void giveSkyWarsKit(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(
                new ItemStack(org.bukkit.Material.STONE_SWORD),
                new ItemStack(org.bukkit.Material.BOW),
                new ItemStack(org.bukkit.Material.ARROW, 16),
                new ItemStack(org.bukkit.Material.COOKED_BEEF, 8),
                new ItemStack(org.bukkit.Material.OAK_PLANKS, 8)
        );
    }

    // ── Win-Bedingung ───────────────────────────────────────────────────

    public void handlePlayerDeath(UUID uuid) {
        players.remove(uuid);
        spectators.add(uuid);

        Player dead = Bukkit.getPlayer(uuid);
        if (dead != null) {
            dead.setGameMode(GameMode.SPECTATOR);
            dead.sendMessage(Component.text("Du bist ausgeschieden! Du schaust jetzt zu.", NamedTextColor.RED));
        }

        broadcast(Component.text((dead != null ? dead.getName() : "Jemand") + " ist ausgeschieden! Noch " + players.size() + " Spieler übrig.", NamedTextColor.RED));
        checkWinCondition();
    }

    private void checkWinCondition() {
        if (state != GameState.RUNNING) return;
        if (players.size() == 1) {
            endGame(players.get(0));
        } else if (players.isEmpty()) {
            endGame(null);
        }
    }

    private void endGame(UUID winner) {
        if (gameTimerTask != null) gameTimerTask.cancel();
        state = GameState.ENDING;

        if (winner != null) {
            Player winPlayer = Bukkit.getPlayer(winner);
            String name = winPlayer != null ? winPlayer.getName() : winner.toString();
            broadcast(Component.text("=== " + name + " hat " + type.getDisplayName() + " gewonnen! ===", NamedTextColor.GOLD));

            // Statistik speichern
            plugin.getStatsManager().incrementStat(winner,
                    type == GameType.BEDWARS ? "bedwars_wins" : "skywars_wins");
        } else {
            broadcast(Component.text("=== Unentschieden! ===", NamedTextColor.GRAY));
        }

        // Nach 5 Sekunden alle Spieler rauswerfen
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new ArrayList<>(players).forEach(this::restorePlayer);
            new ArrayList<>(spectators).forEach(this::restorePlayer);
            players.clear();
            spectators.clear();
            state = GameState.WAITING;
        }, 100L);
    }

    private void restorePlayer(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;

        GameMode gm = previousGameModes.getOrDefault(uuid, GameMode.SURVIVAL);
        ItemStack[] inv = previousInventories.get(uuid);

        p.setGameMode(gm);
        p.getInventory().clear();
        if (inv != null) p.getInventory().setContents(inv);
        p.setHealth(20.0);
        p.setFoodLevel(20);

        previousGameModes.remove(uuid);
        previousInventories.remove(uuid);
    }

    public void forceStop() {
        if (countdownTask != null) countdownTask.cancel();
        if (gameTimerTask != null) gameTimerTask.cancel();
        broadcast(Component.text("Server wird gestoppt – Spiel beendet.", NamedTextColor.RED));
        new ArrayList<>(players).forEach(this::restorePlayer);
        new ArrayList<>(spectators).forEach(this::restorePlayer);
        players.clear();
        spectators.clear();
    }

    private void broadcast(Component msg) {
        players.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        });
        spectators.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        });
    }

    // ── Getter ──────────────────────────────────────────────────────────

    public GameType getType() { return type; }
    public GameState getState() { return state; }
    public int getPlayerCount() { return players.size(); }
    public int getMaxPlayers() { return maxPlayers; }
    public String getArenaId() { return arenaId; }
    public List<UUID> getPlayers() { return players; }
}
