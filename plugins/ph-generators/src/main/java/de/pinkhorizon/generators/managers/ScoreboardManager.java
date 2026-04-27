package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import de.pinkhorizon.generators.listeners.ChatListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

/**
 * Zeigt Sidebar-Scoreboard und Tab-Header/Footer für alle Generators-Spieler.
 */
public class ScoreboardManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private BukkitTask task;

    public ScoreboardManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) removeScoreboard(p);
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
            if (data != null) {
                update(player, data);
                updateTab(player, data);
            }
        }
    }

    public void update(Player player, PlayerData data) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("gen",
                Criteria.DUMMY,
                MM.deserialize("<light_purple><bold>✦ IdleForge ✦</bold>"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        double talentMult = plugin.getTalentManager() != null
                ? plugin.getTalentManager().getIncomeMultiplier(data) : 1.0;
        int talentSlots = plugin.getTalentManager() != null
                ? plugin.getTalentManager().getExtraGeneratorSlots(data) : 0;
        double totalIncome = data.getGenerators().stream()
                .mapToDouble(g -> g.incomePerSecond()
                        * data.prestigeMultiplier()
                        * data.effectiveBoosterMultiplier()
                        * plugin.getMoneyManager().getServerBoosterMultiplier()
                        * plugin.getSynergyManager().getTotalSynergyMultiplier(data)
                        * data.getRankMultiplier()
                        * talentMult
                        * data.getPetIncomeMultiplier())
                .sum();

        // Booster-Zeile (leer wenn kein Booster aktiv)
        String boosterLine = data.hasActiveBooster()
                ? "§6⚡ §ex" + data.getBoosterMultiplier() + " Booster"
                : (plugin.getMoneyManager().isServerBoosterActive()
                        ? "§6⚡ §eServer-Boost aktiv"
                        : "");

        // Pet XP-Fortschritt
        String petXpBar;
        if (data.isPetMaxLevel()) {
            petXpBar = "§6★ MAX";
        } else {
            int filled = (int) Math.round((double) data.getPetXp() / data.petXpToNextLevel() * 5);
            petXpBar = "§a" + "█".repeat(filled) + "§8" + "█".repeat(5 - filled)
                    + " §7" + data.getPetXp() + "§8/§7" + data.petXpToNextLevel();
        }

        int line = 15;
        set(obj, "§r", line--);
        set(obj, "§eGuthaben", line--);
        set(obj, "§a$" + MoneyManager.formatMoney(data.getMoney()), line--);
        set(obj, "§r§r", line--);
        set(obj, "§6Einkommen/s", line--);
        set(obj, "§a$" + MoneyManager.formatMoney(Math.round(totalIncome)), line--);
        set(obj, "§r§r§r", line--);
        set(obj, "§dPrestige: §f" + data.getPrestige(), line--);
        set(obj, "§r§r§r§r", line--);
        set(obj, "§bGeneratoren: §f" + data.getGenerators().size()
                + "§7/§f" + data.maxGeneratorSlots(
                        plugin.getConfig().getInt("max-generators", 10),
                        plugin.getConfig().getInt("generator-slot-per-prestige", 2),
                        talentSlots), line--);
        set(obj, "§r§r§r§r§r", line--);
        set(obj, "§5⛏ Mining: §fLvl " + data.getMiningLevel()
                + "  §d✦ " + data.getShards() + " Shards", line--);
        set(obj, "§r§r§r§r§r§r", line--);
        set(obj, "§6🐾 Pet: §fLvl " + data.getPetLevel() + " §7/ 500", line--);
        set(obj, petXpBar, line--);
        if (!boosterLine.isEmpty()) {
            set(obj, "§r§r§r§r§r§r§r", line--);
            set(obj, boosterLine, line--);
        }
        set(obj, "§r§r§r§r§r§r§r§r", line--);
        set(obj, "§7play.pinkhorizon.de", line--);

        player.setScoreboard(board);
    }

    public void updateTab(Player player, PlayerData data) {
        // Tab-Kopf/Fuß
        player.sendPlayerListHeaderAndFooter(
                MM.deserialize(
                        "\n<light_purple><bold>✦ IdleForge ✦</bold></light_purple>\n"
                        + "<gray>Pink Horizon Network\n"),
                MM.deserialize(
                        "\n<gray>Prestige: <light_purple>" + data.getPrestige()
                        + " <gray>| Geld: <green>$" + MoneyManager.formatMoney(data.getMoney())
                        + "\n<dark_gray>play.pinkhorizon.de\n")
        );

        // Spieler-Name in der Tab-Liste: LP-Gruppen-Prefix + Prestige-Badge + Name
        // Direkt setzen, da unser eigenes Scoreboard die LP-Teams überlagert.
        String group      = ChatListener.getPrimaryGroup(player);
        Component prefix  = ChatListener.buildPrefix(group);
        Component prestige = ChatListener.buildPrestigeBadge(data);
        TextColor nameColor = de.pinkhorizon.generators.listeners.ChatListener.GROUP_COLOR
                .getOrDefault(group, NamedTextColor.WHITE);
        Component name    = Component.text(player.getName(), nameColor);
        player.playerListName(prefix.append(prestige).append(name));
    }

    public void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void set(Objective obj, String entry, int score) {
        obj.getScore(entry).setScore(score);
    }
}
