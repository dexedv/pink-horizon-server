package de.pinkhorizon.smash.managers;

import de.pinkhorizon.smash.PHSmash;
import de.pinkhorizon.smash.boss.ActiveBoss;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class LootManager {

    public enum LootItem {
        IRON_FRAGMENT ("Eisen-Splitter", "§7"),
        GOLD_FRAGMENT ("Gold-Splitter",  "§6"),
        DIAMOND_SHARD ("Boss-Kristall",  "§b"),
        BOSS_CORE     ("Boss-Kern",      "§5");

        public final String displayName;
        public final String color;

        LootItem(String displayName, String color) {
            this.displayName = displayName;
            this.color       = color;
        }
    }

    private final PHSmash plugin;
    private final Random  rng = new Random();

    public LootManager(PHSmash plugin) {
        this.plugin = plugin;
    }

    /** Pro-Spieler-Loot-Verteilung (eine Arena, ein Spieler) */
    public void distributeSinglePlayer(Player player, int bossLevel) {
        Map<LootItem, Integer> drops = rollLoot(bossLevel, 1.0);
        addToInventory(player.getUniqueId(), drops);
        sendLootMessage(player, drops);
    }

    /** Altes Multi-Spieler-System (für Kompatibilität behalten) */
    public void distributeLoot(ActiveBoss boss) {
        int level = boss.getConfig().level();
        Map<UUID, Double> contrib = boss.getDamageContrib();
        if (contrib.isEmpty()) return;

        UUID topPlayer = contrib.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse(null);

        for (UUID uuid : contrib.keySet()) {
            double bonus = uuid.equals(topPlayer) ? 1.5 : 1.0;
            Map<LootItem, Integer> drops = rollLoot(level, bonus);
            addToInventory(uuid, drops);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) sendLootMessage(player, drops);
        }
    }

    private Map<LootItem, Integer> rollLoot(int level, double bonus) {
        Map<LootItem, Integer> result = new java.util.EnumMap<>(LootItem.class);

        roll(result, LootItem.IRON_FRAGMENT,
            Math.min(0.70 + 0.001 * level, 0.95),
            (int) Math.round((1 + rng.nextInt(3 + level / 10)) * bonus));

        roll(result, LootItem.GOLD_FRAGMENT,
            Math.min(0.30 + 0.001 * level, 0.70),
            (int) Math.round((1 + rng.nextInt(Math.max(1, 2 + level / 20))) * bonus));

        roll(result, LootItem.DIAMOND_SHARD,
            Math.min(0.10 + 0.001 * level, 0.40),
            (int) Math.round((1 + rng.nextInt(Math.max(1, 1 + level / 50))) * bonus));

        roll(result, LootItem.BOSS_CORE,
            Math.min(0.02 + 0.001 * level, 0.15),
            Math.max(1, (int) Math.round(bonus)));

        return result;
    }

    private void roll(Map<LootItem, Integer> result, LootItem item, double chance, int qty) {
        if (rng.nextDouble() < chance) result.put(item, Math.max(1, qty));
    }

    private void addToInventory(UUID uuid, Map<LootItem, Integer> drops) {
        if (drops.isEmpty()) return;
        try (Connection c = plugin.getDb().getConnection()) {
            for (Map.Entry<LootItem, Integer> e : drops.entrySet()) {
                try (PreparedStatement st = c.prepareStatement("""
                    INSERT INTO smash_inventory (uuid, item_type, quantity) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)
                    """)) {
                    st.setString(1, uuid.toString());
                    st.setString(2, e.getKey().name());
                    st.setInt(3, e.getValue());
                    st.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("LootManager.addToInventory: " + e.getMessage());
        }
    }

    private void sendLootMessage(Player player, Map<LootItem, Integer> drops) {
        if (drops.isEmpty()) return;
        StringBuilder sb = new StringBuilder("§a✔ Loot: ");
        boolean first = true;
        for (Map.Entry<LootItem, Integer> e : drops.entrySet()) {
            if (!first) sb.append(" §8| ");
            sb.append(e.getKey().color).append(e.getValue()).append("x §7").append(e.getKey().displayName);
            first = false;
        }
        player.sendMessage(sb.toString());
    }

    public int getQuantity(UUID uuid, LootItem item) {
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "SELECT quantity FROM smash_inventory WHERE uuid = ? AND item_type = ?")) {
            st.setString(1, uuid.toString());
            st.setString(2, item.name());
            try (var rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("quantity");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("LootManager.getQuantity: " + e.getMessage());
        }
        return 0;
    }

    public boolean consume(UUID uuid, LootItem item, int amount) {
        int have = getQuantity(uuid, item);
        if (have < amount) return false;
        try (Connection c = plugin.getDb().getConnection();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE smash_inventory SET quantity = quantity - ? WHERE uuid = ? AND item_type = ?")) {
            st.setInt(1, amount);
            st.setString(2, uuid.toString());
            st.setString(3, item.name());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("LootManager.consume: " + e.getMessage());
        }
        return true;
    }
}
