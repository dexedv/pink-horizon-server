package de.pinkhorizon.minigames.managers;

import de.pinkhorizon.minigames.PHMinigames;
import de.pinkhorizon.minigames.games.GameState;
import de.pinkhorizon.minigames.games.GameType;
import de.pinkhorizon.minigames.games.MiniGame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ArenaManager {

    private final PHMinigames plugin;
    private final List<MiniGame> activeGames = new ArrayList<>();

    public ArenaManager(PHMinigames plugin) {
        this.plugin = plugin;
        // Standard-Arenen erstellen
        activeGames.add(new MiniGame(plugin, "bedwars-1", GameType.BEDWARS, 8));
        activeGames.add(new MiniGame(plugin, "skywars-1", GameType.SKYWARS, 12));
    }

    public Optional<MiniGame> getWaitingGame(GameType type) {
        return activeGames.stream()
                .filter(g -> g.getType() == type && g.getState() == GameState.WAITING)
                .filter(g -> g.getPlayerCount() < g.getMaxPlayers())
                .findFirst();
    }

    public boolean joinGame(UUID player, GameType type) {
        Optional<MiniGame> game = getWaitingGame(type);
        if (game.isEmpty()) return false;
        return game.get().addPlayer(player);
    }

    public List<MiniGame> getAllGames() {
        return activeGames;
    }

    public void stopAll() {
        activeGames.forEach(MiniGame::forceStop);
    }
}
