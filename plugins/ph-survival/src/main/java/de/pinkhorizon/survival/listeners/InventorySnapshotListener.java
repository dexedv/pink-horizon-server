package de.pinkhorizon.survival.listeners;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class InventorySnapshotListener implements Listener {

    private final PHSurvival plugin;

    public InventorySnapshotListener(PHSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        saveSnapshot(event.getPlayer());
    }

    public void saveSnapshot(Player player) {
        UUID uuid = player.getUniqueId();
        String json = serializeInventory(player);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = plugin.getSurvivalDb().getConnection();
                 PreparedStatement st = con.prepareStatement(
                     "INSERT INTO sv_inventory_snapshot (uuid, snapshot_time, inventory_json) VALUES (?,?,?) " +
                     "ON DUPLICATE KEY UPDATE snapshot_time=VALUES(snapshot_time), inventory_json=VALUES(inventory_json)")) {
                st.setString(1, uuid.toString());
                st.setLong(2, System.currentTimeMillis());
                st.setString(3, json);
                st.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("InventorySnapshot: " + e.getMessage());
            }
        });
    }

    private String serializeInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"slot\":").append(i)
              .append(",\"material\":\"").append(item.getType().name()).append("\"")
              .append(",\"amount\":").append(item.getAmount());
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                sb.append(",\"name\":\"").append(jsonEsc(name)).append("\"");
            }
            if (meta != null && !meta.getEnchants().isEmpty()) {
                sb.append(",\"enchants\":[");
                boolean fe = true;
                for (var entry : meta.getEnchants().entrySet()) {
                    if (!fe) sb.append(",");
                    fe = false;
                    sb.append("\"").append(entry.getKey().getKey().getKey())
                      .append(" ").append(entry.getValue()).append("\"");
                }
                sb.append("]");
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonEsc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
