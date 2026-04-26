package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Belohnt Spieler für das Erreichen von Geld-Meilensteinen (basierend auf totalEarned).
 */
public class MilestoneManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private record Milestone(long target, long moneyReward, int tokens, int talentPts, String title) {}

    private static final Milestone[] MILESTONES = {
        new Milestone(    1_000_000L,      50_000L, 0, 1, "<green>Millionär"),
        new Milestone(   10_000_000L,     200_000L, 1, 1, "<aqua>Zehn-Millionär"),
        new Milestone(  100_000_000L,   1_000_000L, 1, 2, "<gold>100-Millionär"),
        new Milestone(1_000_000_000L,   5_000_000L, 2, 2, "<light_purple>Milliardär"),
        new Milestone(10_000_000_000L, 20_000_000L, 3, 3, "<dark_purple>Zehn-Milliardär"),
        new Milestone(100_000_000_000L,100_000_000L, 5, 5, "<red><bold>Hundert-Milliardär"),
        new Milestone(1_000_000_000_000L,500_000_000L,10,10,"<dark_red><bold>BILLIONÄR ❋"),
    };

    public MilestoneManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    /**
     * Wird jede Sekunde aus MoneyManager aufgerufen.
     */
    public void check(UUID uuid, PlayerData data) {
        int current = data.getMilestoneReached();
        if (current >= MILESTONES.length) return;

        Milestone next = MILESTONES[current];
        if (data.getTotalEarned() >= next.target()) {
            data.setMilestoneReached(current + 1);
            awardMilestone(uuid, data, current, next);
        }
    }

    private void awardMilestone(UUID uuid, PlayerData data, int idx, Milestone m) {
        data.addMoney(m.moneyReward());
        if (m.tokens() > 0) data.addUpgradeTokens(m.tokens());
        if (m.talentPts() > 0) data.addTalentPoints(m.talentPts());

        String medals = "✦".repeat(Math.min(idx + 1, 5));
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.sendMessage(MM.deserialize(
                    "<gold>━━ " + medals + " MEILENSTEIN ERREICHT! " + medals + " ━━\n"
                    + m.title() + "\n"
                    + "<gray>Belohnung: <green>+$" + MoneyManager.formatMoney(m.moneyReward())
                    + (m.tokens() > 0 ? " <aqua>+" + m.tokens() + " Token" : "")
                    + (m.talentPts() > 0 ? " <light_purple>+" + m.talentPts() + " Talentpunkte" : "")));
        }

        // Serverweite Broadcast für Top-Meilensteine
        if (idx >= 3) {
            String name = p != null ? p.getName() : uuid.toString().substring(0, 8);
            Bukkit.broadcast(MM.deserialize(
                    "<gold>✦ <white>" + name + " <gold>hat den Meilenstein erreicht: " + m.title()));
        }

        plugin.getRepository().savePlayer(data);
    }

    public static String getMilestoneInfo(PlayerData data) {
        int current = data.getMilestoneReached();
        StringBuilder sb = new StringBuilder("<gold>━━ Meilensteine ━━\n");
        for (int i = 0; i < MILESTONES.length; i++) {
            Milestone m = MILESTONES[i];
            boolean done = (i < current);
            boolean active = (i == current);
            String prefix = done ? "<green>✔ " : (active ? "<yellow>▶ " : "<dark_gray>○ ");
            String progress = active
                    ? " <gray>[" + MoneyManager.formatMoney(data.getTotalEarned()) + "/" + MoneyManager.formatMoney(m.target()) + "]"
                    : "";
            sb.append(prefix).append(m.title()).append(progress).append("\n");
        }
        return sb.toString().trim();
    }
}
