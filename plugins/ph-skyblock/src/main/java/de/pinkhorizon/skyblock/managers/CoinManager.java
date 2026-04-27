package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.database.GeneratorRepository;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Fassade für Coins-Operationen – alle anderen Manager rufen nur diese Klasse.
 */
public class CoinManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PHSkyBlock plugin;
    private final GeneratorRepository repo;

    public CoinManager(PHSkyBlock plugin, GeneratorRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    /** Stellt sicher dass ein Datensatz für den Spieler existiert. */
    public void ensurePlayer(UUID uuid) {
        repo.ensurePlayerExt(uuid);
    }

    public long getCoins(UUID uuid) {
        return repo.getCoins(uuid);
    }

    public void addCoins(UUID uuid, long amount) {
        if (amount <= 0) return;
        repo.addCoins(uuid, amount);

        // Achievement-Prüfung nach dem Hinzufügen
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            plugin.getAchievementManager().checkCoinAchievements(player);
        }
    }

    /** Gibt true zurück wenn genug Coins vorhanden und zieht sie ab. */
    public boolean deductCoins(UUID uuid, long amount) {
        return repo.deductCoins(uuid, amount);
    }

    /** Schickt eine Nachricht mit dem aktuellen Kontostand. */
    public void sendBalance(Player player) {
        long coins = getCoins(player.getUniqueId());
        player.sendMessage(MM.deserialize(
            "<dark_gray>[<light_purple><bold>SkyBlock</bold></light_purple><dark_gray>] "
            + "<gray>Dein Kontostand: <gold><bold>"
            + String.format("%,d", coins) + " Coins"));
    }
}
