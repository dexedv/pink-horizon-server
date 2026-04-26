package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Verwaltet das Gildensystem.
 * Max. 5 Mitglieder pro Gilde.
 * Gilden-Perk: +10% Einkommen wenn alle Mitglieder online.
 */
public class GuildManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MAX_MEMBERS = 5;

    /** guildId → Guildname */
    private final Map<Integer, String> guildNames = new HashMap<>();
    /** UUID → guildId */
    private final Map<UUID, Integer> memberGuild = new HashMap<>();
    /** guildId → Leader-UUID */
    private final Map<Integer, UUID> guildLeaders = new HashMap<>();
    /** guildId → Mitglieder-UUIDs */
    private final Map<Integer, List<UUID>> guildMembers = new HashMap<>();

    public GuildManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    // ── Gilde erstellen ───────────────────────────────────────────────────────

    public CreateResult create(Player player, String name) {
        if (memberGuild.containsKey(player.getUniqueId())) return CreateResult.ALREADY_IN_GUILD;
        if (name.length() < 3 || name.length() > 16) return CreateResult.INVALID_NAME;
        if (guildNames.containsValue(name)) return CreateResult.NAME_TAKEN;

        int id = plugin.getRepository().createGuild(name, player.getUniqueId());
        if (id < 0) return CreateResult.DB_ERROR;

        plugin.getRepository().addGuildMember(id, player.getUniqueId());

        guildNames.put(id, name);
        guildLeaders.put(id, player.getUniqueId());
        memberGuild.put(player.getUniqueId(), id);
        guildMembers.computeIfAbsent(id, k -> new ArrayList<>()).add(player.getUniqueId());

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data != null) plugin.getAchievementManager().track(data, "guild_create", 1);

        player.sendMessage(MM.deserialize("<green>✔ Gilde <gold>" + name + " <green>erstellt!"));
        return CreateResult.SUCCESS;
    }

    // ── Gilde beitreten ───────────────────────────────────────────────────────

    public JoinResult join(Player player, String guildName) {
        if (memberGuild.containsKey(player.getUniqueId())) return JoinResult.ALREADY_IN_GUILD;

        Integer guildId = findGuildId(guildName);
        if (guildId == null) return JoinResult.NOT_FOUND;

        List<UUID> members = guildMembers.getOrDefault(guildId, new ArrayList<>());
        if (members.size() >= MAX_MEMBERS) return JoinResult.FULL;

        plugin.getRepository().addGuildMember(guildId, player.getUniqueId());
        memberGuild.put(player.getUniqueId(), guildId);
        members.add(player.getUniqueId());
        guildMembers.put(guildId, members);

        broadcastToGuild(guildId, "<green>" + player.getName() + " ist der Gilde beigetreten!");
        return JoinResult.SUCCESS;
    }

    // ── Gilde verlassen ───────────────────────────────────────────────────────

    public LeaveResult leave(Player player) {
        Integer guildId = memberGuild.remove(player.getUniqueId());
        if (guildId == null) return LeaveResult.NOT_IN_GUILD;

        plugin.getRepository().removeGuildMember(player.getUniqueId());
        List<UUID> members = guildMembers.getOrDefault(guildId, new ArrayList<>());
        members.remove(player.getUniqueId());

        if (members.isEmpty() || guildLeaders.get(guildId).equals(player.getUniqueId())) {
            // Gilde auflösen wenn leer oder Leader geht
            dissolveGuild(guildId);
        } else {
            broadcastToGuild(guildId, "<yellow>" + player.getName() + " hat die Gilde verlassen.");
        }
        player.sendMessage(MM.deserialize("<yellow>Du hast die Gilde verlassen."));
        return LeaveResult.SUCCESS;
    }

    private void dissolveGuild(int guildId) {
        plugin.getRepository().deleteGuild(guildId);
        List<UUID> members = guildMembers.remove(guildId);
        if (members != null) members.forEach(memberGuild::remove);
        guildNames.remove(guildId);
        guildLeaders.remove(guildId);
        Bukkit.broadcast(MM.deserialize("<gray>Eine Gilde wurde aufgelöst."));
    }

    // ── Perk: Alle online → +10% ──────────────────────────────────────────────

    /**
     * Gibt den Gildenperk-Multiplikator zurück.
     * +10% wenn alle Mitglieder der Gilde online sind (mind. 2 Mitglieder).
     */
    public double getGuildPerkMultiplier(UUID uuid) {
        Integer guildId = memberGuild.get(uuid);
        if (guildId == null) return 1.0;

        List<UUID> members = guildMembers.getOrDefault(guildId, new ArrayList<>());
        if (members.size() < 2) return 1.0;

        boolean allOnline = members.stream().allMatch(m -> Bukkit.getPlayer(m) != null);
        return allOnline ? 1.10 : 1.0;
    }

    // ── Info ─────────────────────────────────────────────────────────────────

    public String getGuildInfo(UUID uuid) {
        Integer guildId = memberGuild.get(uuid);
        if (guildId == null) return "<gray>Du bist in keiner Gilde. <yellow>/gen guild create <name>";

        String name = guildNames.get(guildId);
        List<UUID> members = guildMembers.getOrDefault(guildId, new ArrayList<>());
        UUID leader = guildLeaders.get(guildId);

        StringBuilder sb = new StringBuilder("<gold>━━ Gilde: " + name + " ━━\n");
        sb.append("<gray>Mitglieder (").append(members.size()).append("/").append(MAX_MEMBERS).append("):\n");
        for (UUID m : members) {
            Player p = Bukkit.getPlayer(m);
            String status = p != null ? "<green>● " : "<red>● ";
            String pname = p != null ? p.getName() : m.toString().substring(0, 8) + "...";
            boolean isLeader = m.equals(leader);
            sb.append(" ").append(status).append("<white>").append(pname);
            if (isLeader) sb.append(" <gold>[Leader]");
            sb.append("\n");
        }
        boolean perk = getGuildPerkMultiplier(uuid) > 1.0;
        sb.append(perk ? "<green>✔ Gilden-Perk aktiv (+10%)" : "<red>✗ Gilden-Perk inaktiv (nicht alle online)");
        return sb.toString();
    }

    /** Top-Gilden nach Gesamteinkommen */
    public String getGuildTop() {
        Map<Integer, Long> guildMoney = new HashMap<>();
        for (Map.Entry<Integer, List<UUID>> entry : guildMembers.entrySet()) {
            long total = 0;
            for (UUID m : entry.getValue()) {
                PlayerData data = plugin.getPlayerDataMap().get(m);
                if (data != null) total += data.getMoney();
            }
            guildMoney.put(entry.getKey(), total);
        }

        StringBuilder sb = new StringBuilder("<gold>━━ Top-Gilden ━━\n");
        guildMoney.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    String gName = guildNames.getOrDefault(e.getKey(), "?");
                    sb.append("<gray>• <white>").append(gName)
                            .append(" <gold>$").append(MoneyManager.formatMoney(e.getValue())).append("\n");
                });
        return sb.toString().trim();
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private Integer findGuildId(String name) {
        return guildNames.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(name))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    private void broadcastToGuild(int guildId, String message) {
        List<UUID> members = guildMembers.getOrDefault(guildId, new ArrayList<>());
        for (UUID m : members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) p.sendMessage(MM.deserialize("<gold>[Gilde] <white>" + message));
        }
    }

    public enum CreateResult { SUCCESS, ALREADY_IN_GUILD, INVALID_NAME, NAME_TAKEN, DB_ERROR }
    public enum JoinResult   { SUCCESS, ALREADY_IN_GUILD, NOT_FOUND, FULL }
    public enum LeaveResult  { SUCCESS, NOT_IN_GUILD }
}
