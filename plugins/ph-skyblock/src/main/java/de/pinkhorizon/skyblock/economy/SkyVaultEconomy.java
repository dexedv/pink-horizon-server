package de.pinkhorizon.skyblock.economy;

import de.pinkhorizon.skyblock.PHSkyBlock;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Registriert ph-skyblock's Coin-System als Vault-Economy-Provider.
 * Ermöglicht es BentoBox-Addons (Challenges, MagicCobblestoneGenerator, Visit)
 * Coins als Währung zu nutzen.
 */
public class SkyVaultEconomy implements Economy {

    private final PHSkyBlock plugin;

    public SkyVaultEconomy(PHSkyBlock plugin) {
        this.plugin = plugin;
    }

    @Override public boolean isEnabled()            { return plugin.isEnabled(); }
    @Override public String getName()               { return "SkyCoins"; }
    @Override public boolean hasBankSupport()       { return false; }
    @Override public int fractionalDigits()         { return 0; }
    @Override public String currencyNamePlural()    { return "Coins"; }
    @Override public String currencyNameSingular()  { return "Coin"; }

    @Override
    public String format(double amount) {
        return String.format("%,.0f Coins", amount);
    }

    // ── Account-Checks ────────────────────────────────────────────────────────

    @Override
    public boolean hasAccount(String playerName) {
        return getUUID(playerName) != null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    @Override
    public double getBalance(String playerName) {
        UUID uuid = getUUID(playerName);
        if (uuid == null) return 0;
        return plugin.getCoinManager().getCoins(uuid);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null) return 0;
        return plugin.getCoinManager().getCoins(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // ── Abbuchen ──────────────────────────────────────────────────────────────

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        UUID uuid = getUUID(playerName);
        if (uuid == null) return notFound(playerName);
        return withdraw(uuid, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) return notFound("?");
        return withdraw(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    private EconomyResponse withdraw(UUID uuid, double amount) {
        long amt = (long) amount;
        boolean ok = plugin.getCoinManager().deductCoins(uuid, amt);
        if (!ok) {
            double bal = plugin.getCoinManager().getCoins(uuid);
            return new EconomyResponse(amount, bal, EconomyResponse.ResponseType.FAILURE, "Nicht genug Coins.");
        }
        double newBal = plugin.getCoinManager().getCoins(uuid);
        return new EconomyResponse(amount, newBal, EconomyResponse.ResponseType.SUCCESS, null);
    }

    // ── Einzahlen ─────────────────────────────────────────────────────────────

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        UUID uuid = getUUID(playerName);
        if (uuid == null) return notFound(playerName);
        return deposit(uuid, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) return notFound("?");
        return deposit(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    private EconomyResponse deposit(UUID uuid, double amount) {
        plugin.getCoinManager().addCoins(uuid, (long) amount);
        double newBal = plugin.getCoinManager().getCoins(uuid);
        return new EconomyResponse(amount, newBal, EconomyResponse.ResponseType.SUCCESS, null);
    }

    // ── Bank (nicht unterstützt) ──────────────────────────────────────────────

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override public EconomyResponse createBank(String name, String player)       { return notImplemented(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notImplemented(); }
    @Override public EconomyResponse deleteBank(String name)                       { return notImplemented(); }
    @Override public EconomyResponse bankBalance(String name)                      { return notImplemented(); }
    @Override public EconomyResponse bankHas(String name, double amount)           { return notImplemented(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount)      { return notImplemented(); }
    @Override public EconomyResponse bankDeposit(String name, double amount)       { return notImplemented(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName)   { return notImplemented(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player){ return notImplemented(); }
    @Override public EconomyResponse isBankMember(String name, String playerName)  { return notImplemented(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player){ return notImplemented(); }
    @Override public List<String> getBanks()                                        { return Collections.emptyList(); }

    // ── Account erstellen ─────────────────────────────────────────────────────

    @Override
    public boolean createPlayerAccount(String playerName) {
        UUID uuid = getUUID(playerName);
        if (uuid == null) return false;
        plugin.getCoinManager().ensurePlayer(uuid);
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) return false;
        plugin.getCoinManager().ensurePlayer(player.getUniqueId());
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // ── Hilfsmethode ─────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private UUID getUUID(String name) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(name);
        return p.hasPlayedBefore() || p.isOnline() ? p.getUniqueId() : null;
    }

    private EconomyResponse notFound(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE,
            "Spieler " + name + " nicht gefunden.");
    }
}
