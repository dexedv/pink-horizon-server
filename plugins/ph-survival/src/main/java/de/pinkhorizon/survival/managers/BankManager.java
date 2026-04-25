package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

public class BankManager {


    private final PHSurvival plugin;

    public BankManager(PHSurvival plugin) {
        this.plugin = plugin;
        // Hourly interest check (72000 ticks = 1 h)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkInterest, 72000L, 72000L);
    }

    private Connection con() throws SQLException {
        return plugin.getSurvivalDb().getConnection();
    }

    public long getBalance(UUID uuid) {
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "SELECT balance FROM sv_bank WHERE uuid=?")) {
            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("BankManager.getBalance: " + e.getMessage());
        }
        return 0L;
    }

    public boolean deposit(UUID uuid, long amount) {
        if (amount <= 0) return false;
        if (!plugin.getEconomyManager().has(uuid, amount)) return false;
        plugin.getEconomyManager().withdraw(uuid, amount);
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "INSERT INTO sv_bank (uuid, balance) VALUES (?,?)" +
                 " ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)")) {
            st.setString(1, uuid.toString());
            st.setLong(2, amount);
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("BankManager.deposit: " + e.getMessage());
            return false;
        }
        return true;
    }

    public boolean withdraw(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long current = getBalance(uuid);
        if (current < amount) return false;
        try (Connection c = con();
             PreparedStatement st = c.prepareStatement(
                 "UPDATE sv_bank SET balance = balance - ? WHERE uuid=?")) {
            st.setLong(1, amount);
            st.setString(2, uuid.toString());
            st.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("BankManager.withdraw: " + e.getMessage());
            return false;
        }
        plugin.getEconomyManager().deposit(uuid, amount);
        return true;
    }

    private void checkInterest() {
        LocalDate today = LocalDate.now();
        try (Connection c = con();
             PreparedStatement sel = c.prepareStatement(
                 "SELECT uuid, balance, last_interest FROM sv_bank WHERE balance > 0");
             ResultSet rs = sel.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID      uuid    = UUID.fromString(rs.getString("uuid"));
                    long      balance = rs.getLong("balance");
                    LocalDate last    = rs.getDate("last_interest").toLocalDate();
                    if (!today.isAfter(last)) continue;

                    SurvivalRankManager.Rank rank = plugin.getRankManager().getRank(uuid);
                    long interest = Math.round(Math.min(balance, rank.maxInterestBase) * rank.interestRate);
                    if (interest < 1) continue;

                    try (PreparedStatement upd = c.prepareStatement(
                             "UPDATE sv_bank SET balance = balance + ?, last_interest = ? WHERE uuid=?")) {
                        upd.setLong(1, interest);
                        upd.setDate(2, Date.valueOf(today));
                        upd.setString(3, uuid.toString());
                        upd.executeUpdate();
                    }

                    final long fi = interest;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            SurvivalRankManager.Rank r = plugin.getRankManager().getRank(uuid);
                            int pct = (int) Math.round(r.interestRate * 100);
                            p.sendMessage(Component.text("§6§lBank §8| §7Zinsen: §6+" + fi + " Coins §8(" + pct + "%)"));
                        }
                    });
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("BankManager.checkInterest: " + e.getMessage());
        }
    }
}
