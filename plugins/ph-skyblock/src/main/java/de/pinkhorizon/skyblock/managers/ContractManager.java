package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Kontrakt-System: Spieler können Aufträge für andere Spieler erstellen.
 * Belohnungen werden im Escrow gehalten bis Auftrag erfüllt.
 */
public class ContractManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum ContractType {
        DELIVERY ("Lieferauftrag",  "Liefere Items an den Auftraggeber"),
        FARMING  ("Farming",       "Ernte eine bestimmte Menge Pflanzen"),
        COMMUNITY("Gemeinschaft",  "Server-weites Ziel – alle helfen mit"),
        PVE      ("PvE",           "Töte eine bestimmte Anzahl Mobs");

        public final String displayName;
        public final String description;
        ContractType(String d, String desc) { displayName = d; description = desc; }
    }

    public record Contract(
        long id,
        UUID creator,
        ContractType type,
        String requirement,   // JSON-ähnliche Beschreibung
        long rewardCoins,
        long deadline,        // Unix-Timestamp (ms)
        boolean completed,
        long progress,
        long goal
    ) {}

    private final PHSkyBlock plugin;

    public ContractManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        createTables();
        startExpiryCheck();
    }

    private void createTables() {
        try (Connection c = plugin.getDatabase().getConnection();
             Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_contracts (
                    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                    creator      VARCHAR(36)  NOT NULL,
                    type         VARCHAR(32)  NOT NULL,
                    requirement  VARCHAR(256) NOT NULL,
                    reward_coins BIGINT       DEFAULT 0,
                    goal         BIGINT       DEFAULT 1,
                    progress     BIGINT       DEFAULT 0,
                    deadline     BIGINT       NOT NULL,
                    completed    TINYINT      DEFAULT 0,
                    INDEX idx_type (type),
                    INDEX idx_creator (creator)
                )
            """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_contract_participants (
                    contract_id  BIGINT      NOT NULL,
                    uuid         VARCHAR(36) NOT NULL,
                    contribution BIGINT      DEFAULT 0,
                    PRIMARY KEY (contract_id, uuid)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Kontrakt-Tabellen Fehler: " + e.getMessage());
        }
    }

    // ── Erstellen ─────────────────────────────────────────────────────────────

    /**
     * Erstellt einen neuen Lieferauftrag.
     * @param creator     Ersteller-UUID
     * @param item        gesuchtes Material
     * @param amount      gesuchte Menge
     * @param reward      Belohnung in Coins
     * @param daysUntilDeadline Frist in Tagen
     */
    public boolean createDeliveryContract(UUID creator, Material item, long amount,
                                           long reward, int daysUntilDeadline) {
        if (!plugin.getCoinManager().deductCoins(creator, reward)) {
            Player p = Bukkit.getPlayer(creator);
            if (p != null) p.sendMessage(MM.deserialize("<red>Nicht genug Coins für den Auftrag-Escrow."));
            return false;
        }
        long deadline = System.currentTimeMillis() + (long) daysUntilDeadline * 86400 * 1000;
        String req = item.name() + " × " + amount;

        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO sky_contracts (creator,type,requirement,reward_coins,goal,deadline) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, creator.toString());
            ps.setString(2, ContractType.DELIVERY.name());
            ps.setString(3, req);
            ps.setLong  (4, reward);
            ps.setLong  (5, amount);
            ps.setLong  (6, deadline);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Kontrakt erstellen fehlgeschlagen: " + e.getMessage());
            plugin.getCoinManager().addCoins(creator, reward); // Escrow zurück
            return false;
        }

        Player p = Bukkit.getPlayer(creator);
        if (p != null) p.sendMessage(MM.deserialize(
            "<green>Auftrag erstellt: <white>" + req + " <green>Belohnung: <gold>"
            + String.format("%,d", reward) + " Coins"));
        return true;
    }

    // ── Anzeige ───────────────────────────────────────────────────────────────

    public void showBoard(Player player) {
        new de.pinkhorizon.skyblock.gui.ContractGui(plugin, player).open(player);
    }

    @Deprecated
    public void showBoardChat(Player player) {
        List<Contract> contracts = loadActive();
        player.sendMessage(MM.deserialize("<gold>━━━ <bold>Auftrags-Brett</bold> ━━━━━━━━━━━━"));
        if (contracts.isEmpty()) {
            player.sendMessage(MM.deserialize("<gray>Keine aktiven Aufträge."));
        } else {
            for (Contract ct : contracts) {
                String deadline = new SimpleDateFormat("dd.MM HH:mm")
                    .format(new Date(ct.deadline()));
                String progress = ct.type() == ContractType.COMMUNITY
                    ? ct.progress() + "/" + ct.goal()
                    : "";
                player.sendMessage(MM.deserialize(
                    "<yellow>#" + ct.id() + " <white>" + ct.requirement()
                    + " <dark_gray>│ <gold>" + String.format("%,d", ct.rewardCoins()) + " Coins"
                    + (progress.isEmpty() ? "" : " <gray>[" + progress + "]")
                    + " <dark_gray>│ <gray>bis " + deadline));
            }
        }
        player.sendMessage(MM.deserialize("<gray>Aufträge annehmen: <yellow>/contract accept <id>"));
        player.sendMessage(MM.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    /** Nimmt einen Auftrag per ID an (GUI-Klick). */
    public void acceptContract(Player player, long contractId) {
        List<Contract> active = loadActive();
        Contract ct = active.stream().filter(c -> c.id() == contractId).findFirst().orElse(null);
        if (ct == null) {
            player.sendMessage(MM.deserialize("<red>Auftrag #" + contractId + " nicht gefunden."));
            return;
        }
        player.sendMessage(MM.deserialize("<green>✔ Auftrag <yellow>#" + contractId + " <green>angenommen!"));
        player.sendMessage(MM.deserialize("<gray>Ziel: <white>" + ct.requirement()
            + " <dark_gray>│ <gold>" + String.format("%,d", ct.rewardCoins()) + " Coins Belohnung"));
    }

    public List<Contract> loadActive() {
        List<Contract> result = new ArrayList<>();
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id,creator,type,requirement,reward_coins,goal,progress,deadline FROM sky_contracts " +
                 "WHERE completed=0 AND deadline > ? ORDER BY id DESC LIMIT 20")) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Contract(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("creator")),
                        ContractType.valueOf(rs.getString("type")),
                        rs.getString("requirement"),
                        rs.getLong("reward_coins"),
                        rs.getLong("deadline"),
                        false,
                        rs.getLong("progress"),
                        rs.getLong("goal")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Kontrakte laden fehlgeschlagen: " + e.getMessage());
        }
        return result;
    }

    // ── Community-Fortschritt ─────────────────────────────────────────────────

    public void addCommunityProgress(long contractId, UUID contributor, long amount) {
        try (Connection c = plugin.getDatabase().getConnection()) {
            // Progress updaten
            try (PreparedStatement ps = c.prepareStatement(
                "UPDATE sky_contracts SET progress=progress+? WHERE id=? AND completed=0")) {
                ps.setLong(1, amount);
                ps.setLong(2, contractId);
                ps.executeUpdate();
            }
            // Teilnehmer updaten
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sky_contract_participants (contract_id,uuid,contribution) VALUES(?,?,?) " +
                "ON DUPLICATE KEY UPDATE contribution=contribution+VALUES(contribution)")) {
                ps.setLong  (1, contractId);
                ps.setString(2, contributor.toString());
                ps.setLong  (3, amount);
                ps.executeUpdate();
            }
            // Prüfen ob Ziel erreicht
            checkCommunityCompletion(c, contractId);
        } catch (SQLException e) {
            plugin.getLogger().warning("Community-Progress Fehler: " + e.getMessage());
        }
    }

    private void checkCommunityCompletion(Connection c, long contractId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT progress,goal,reward_coins FROM sky_contracts WHERE id=? AND completed=0")) {
            ps.setLong(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                long progress = rs.getLong("progress");
                long goal     = rs.getLong("goal");
                if (progress < goal) return;

                // Abschließen
                try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE sky_contracts SET completed=1 WHERE id=?")) {
                    upd.setLong(1, contractId);
                    upd.executeUpdate();
                }

                // Server-Ankündigung
                long reward = rs.getLong("reward_coins");
                String msg = "<gold>✓ <yellow>Community-Auftrag #" + contractId
                    + " abgeschlossen! Server-weiter 2x-Booster für 2 Stunden aktiviert!";
                Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(MM.deserialize(msg)));
            }
        }
    }

    private void startExpiryCheck() {
        // Alle 5 Minuten abgelaufene Aufträge bereinigen
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabase().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE sky_contracts SET completed=1 WHERE deadline < ? AND completed=0")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }, 20L * 60 * 5, 20L * 60 * 5);
    }
}
