package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.data.IslandDna;
import de.pinkhorizon.skyblock.enums.IslandGene;
import de.pinkhorizon.skyblock.integration.BentoBoxHook;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Verwaltet die Insel-DNA: Zufällige Gene pro Insel, Anzeige, Kombinations-System.
 */
public class IslandDnaManager {

    private static final MiniMessage MM    = MiniMessage.miniMessage();
    private static final int  GENES_PER_ISLAND = 5;
    private static final long COMBINE_COST     = 1_000_000L;

    private final PHSkyBlock plugin;
    private final Map<UUID, IslandDna> cache = new ConcurrentHashMap<>();
    private final Random rng = new Random();

    public IslandDnaManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        createTables();
    }

    // ── Tabellen ──────────────────────────────────────────────────────────────

    private void createTables() {
        try (Connection c = plugin.getDatabase().getConnection();
             Statement  s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_island_dna (
                    island_uuid       VARCHAR(36)  PRIMARY KEY,
                    genes             VARCHAR(512) NOT NULL,
                    combinations_used INT          DEFAULT 0
                )
            """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sky_dna_fragments (
                    uuid        VARCHAR(36) NOT NULL,
                    fragment_id VARCHAR(64) NOT NULL,
                    amount      INT         DEFAULT 0,
                    PRIMARY KEY (uuid, fragment_id)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("DNA-Tabellen Fehler: " + e.getMessage());
        }
    }

    // ── DNA laden / erstellen ─────────────────────────────────────────────────

    public IslandDna getDna(UUID islandUuid) {
        return cache.computeIfAbsent(islandUuid, this::loadOrCreate);
    }

    public void assignNewDna(UUID islandUuid) {
        IslandDna dna = createRandom(islandUuid);
        cache.put(islandUuid, dna);
    }

    private IslandDna loadOrCreate(UUID islandUuid) {
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT genes, combinations_used FROM sky_island_dna WHERE island_uuid=?")) {
            ps.setString(1, islandUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    List<IslandGene> genes = parseGenes(rs.getString("genes"));
                    return new IslandDna(islandUuid, genes, rs.getInt("combinations_used"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DNA laden fehlgeschlagen: " + e.getMessage());
        }
        return createRandom(islandUuid);
    }

    private IslandDna createRandom(UUID islandUuid) {
        IslandGene[] all   = IslandGene.values();
        List<IslandGene> pool = new ArrayList<>(Arrays.asList(all));
        List<IslandGene> chosen = new ArrayList<>();
        for (int i = 0; i < GENES_PER_ISLAND && !pool.isEmpty(); i++) {
            chosen.add(pool.remove(rng.nextInt(pool.size())));
        }
        IslandDna dna = new IslandDna(islandUuid, Collections.unmodifiableList(chosen), 0);
        saveDna(dna);
        return dna;
    }

    private void saveDna(IslandDna dna) {
        String genesStr = dna.genes().stream().map(Enum::name).collect(Collectors.joining(","));
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO sky_island_dna (island_uuid,genes,combinations_used) VALUES(?,?,?) " +
                 "ON DUPLICATE KEY UPDATE genes=VALUES(genes), combinations_used=VALUES(combinations_used)")) {
            ps.setString(1, dna.islandUuid().toString());
            ps.setString(2, genesStr);
            ps.setInt   (3, dna.combinationsUsed());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DNA speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    private List<IslandGene> parseGenes(String raw) {
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> { try { return IslandGene.valueOf(s); } catch (IllegalArgumentException x) { return null; } })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    // ── Abfragen ──────────────────────────────────────────────────────────────

    /** Gibt true zurück wenn die Insel des Spielers das Gen hat. */
    public boolean playerHasGene(UUID playerUuid, IslandGene gene) {
        return BentoBoxHook.getIsland(playerUuid)
            .map(i -> getDna(playerUuid).hasGene(gene))
            .orElse(false);
    }

    public double getCropMultiplier(UUID playerUuid) {
        double mult = 1.0;
        if (playerHasGene(playerUuid, IslandGene.FRUITFUL))     mult += 0.20;
        if (playerHasGene(playerUuid, IslandGene.TIME_WARPED))  mult += 0.10;
        if (playerHasGene(playerUuid, IslandGene.LUCKY))        mult += 0.05;
        return mult;
    }

    public double getRitualCooldownMult(UUID playerUuid) {
        return playerHasGene(playerUuid, IslandGene.MYSTICAL) ? 0.75 : 1.0;
    }

    public boolean isStarbound(UUID playerUuid) {
        return playerHasGene(playerUuid, IslandGene.STARBOUND);
    }

    // ── Anzeige ───────────────────────────────────────────────────────────────

    public void showDna(Player player) {
        new de.pinkhorizon.skyblock.gui.DnaGui(plugin, player).open(player);
    }

    @Deprecated
    public void showDnaChat(Player player) {
        var islandOpt = BentoBoxHook.getIsland(player.getUniqueId());
        if (islandOpt.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>Du hast keine Insel."));
            return;
        }
        IslandDna dna = getDna(player.getUniqueId());
        player.sendMessage(MM.deserialize("<light_purple>━━━ <bold>Insel-DNA</bold> ━━━━━━━━━━━━"));
        for (IslandGene g : dna.genes()) {
            player.sendMessage(MM.deserialize("<gold>◆ <white>" + g.displayName + "  <dark_gray>│ " + g.description));
        }
        player.sendMessage(MM.deserialize("<gray>Kombinationen: <yellow>" + dna.combinationsUsed() + "<gray>/3  "
            + "<dark_gray>│  <gray>Kombinieren kostet <gold>1.000.000 Coins"));
        player.sendMessage(MM.deserialize("<light_purple>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    // ── DNA-Fragmente ─────────────────────────────────────────────────────────

    public void addFragment(UUID uuid, String fragmentId, int amount) {
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO sky_dna_fragments (uuid,fragment_id,amount) VALUES(?,?,?) " +
                 "ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, fragmentId);
            ps.setInt   (3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DNA-Fragment speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    public int getFragments(UUID uuid, String fragmentId) {
        try (Connection c = plugin.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT amount FROM sky_dna_fragments WHERE uuid=? AND fragment_id=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, fragmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("amount") : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }
}
