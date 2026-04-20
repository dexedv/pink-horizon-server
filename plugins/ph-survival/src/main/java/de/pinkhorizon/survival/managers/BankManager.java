package de.pinkhorizon.survival.managers;

import de.pinkhorizon.survival.PHSurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class BankManager {

    private static final double INTEREST_RATE  = 0.02;
    private static final long   MAX_INTEREST_BASE = 100_000L;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PHSurvival plugin;
    private File dataFile;
    private YamlConfiguration data;

    public BankManager(PHSurvival plugin) {
        this.plugin = plugin;
        load();
        // Hourly scheduler (72000 ticks = 1 h)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkInterest, 72000L, 72000L);
    }

    public long getBalance(UUID uuid) {
        return data.getLong("bank." + uuid + ".balance", 0L);
    }

    public boolean deposit(UUID uuid, long amount) {
        if (amount <= 0) return false;
        if (!plugin.getEconomyManager().has(uuid, amount)) return false;
        plugin.getEconomyManager().withdraw(uuid, amount);
        data.set("bank." + uuid + ".balance", getBalance(uuid) + amount);
        initLastInterest(uuid);
        save();
        return true;
    }

    public boolean withdraw(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long current = getBalance(uuid);
        if (current < amount) return false;
        plugin.getEconomyManager().deposit(uuid, amount);
        data.set("bank." + uuid + ".balance", current - amount);
        save();
        return true;
    }

    private void initLastInterest(UUID uuid) {
        String path = "bank." + uuid + ".lastInterest";
        if (!data.contains(path)) data.set(path, LocalDate.now().format(DATE_FMT));
    }

    private void checkInterest() {
        var section = data.getConfigurationSection("bank");
        if (section == null) return;
        LocalDate today = LocalDate.now();
        boolean changed = false;

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String lastStr = data.getString("bank." + uuidStr + ".lastInterest");
                LocalDate last = lastStr != null ? LocalDate.parse(lastStr, DATE_FMT) : today.minusDays(1);
                if (!today.isAfter(last)) continue;

                long balance = data.getLong("bank." + uuidStr + ".balance", 0L);
                if (balance <= 0) continue;
                long interest = Math.round(Math.min(balance, MAX_INTEREST_BASE) * INTEREST_RATE);
                if (interest < 1) continue;

                data.set("bank." + uuidStr + ".balance", balance + interest);
                data.set("bank." + uuidStr + ".lastInterest", today.format(DATE_FMT));
                changed = true;

                final long fi = interest;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.sendMessage(Component.text("§6§lBank §8| §7Zinsen: §6+" + fi + " Coins §8(2%)"));
                });
            } catch (Exception ignored) {}
        }
        if (changed) Bukkit.getScheduler().runTask(plugin, this::save);
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "bank.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try { data.save(dataFile); }
        catch (IOException ex) { plugin.getLogger().warning("Bank konnte nicht gespeichert werden: " + ex.getMessage()); }
    }
}
