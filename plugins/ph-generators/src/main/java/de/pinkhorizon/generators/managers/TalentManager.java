package de.pinkhorizon.generators.managers;

import de.pinkhorizon.generators.PHGenerators;
import de.pinkhorizon.generators.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet das Talent-Baum-System.
 * 15 Talente in 3 Tiers – freigeschaltet mit Talentpunkten.
 */
public class TalentManager {

    private final PHGenerators plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public record Talent(String id, String name, String description, int tier,
                         String requiredTalent, int prestigeReq) {}

    // Tier 1 – keine Voraussetzungen
    public static final Talent INCOME_1       = new Talent("income_1",       "<green>Produktivität I",    "+5% Einkommen",          1, null, 0);
    public static final Talent UPGRADE_COST_1 = new Talent("upgrade_cost_1", "<aqua>Sparsamkeit I",       "-5% Upgrade-Kosten",     1, null, 0);
    public static final Talent GENERATOR_SLOT = new Talent("generator_slot", "<yellow>Erweiterung",       "+1 Generator-Slot",      1, null, 0);
    public static final Talent OFFLINE_TIME   = new Talent("offline_time",   "<gray>Fleiß im Schlaf",     "+1h Offline-Cap",        1, null, 0);
    public static final Talent DAILY_BOOST    = new Talent("daily_boost",    "<gold>Treue",               "+25% Tagesbonus",        1, null, 0);

    // Tier 2 – benötigt 3x Tier-1-Talente + Prestige 5
    public static final Talent INCOME_2       = new Talent("income_2",       "<green>Produktivität II",   "+10% Einkommen",         2, "income_1",       5);
    public static final Talent UPGRADE_COST_2 = new Talent("upgrade_cost_2", "<aqua>Sparsamkeit II",      "-10% Upgrade-Kosten",    2, "upgrade_cost_1", 5);
    public static final Talent ENCHANT_CHANCE = new Talent("enchant_chance", "<light_purple>Verzauberung","Doppelte Enchant-Chance",2, "generator_slot", 5);
    public static final Talent SYNERGY_BOOST  = new Talent("synergy_boost",  "<yellow>Synergie-Meister",  "+50% Synergie-Boni",     2, "offline_time",   5);
    public static final Talent BOOSTER_EXTEND = new Talent("booster_extend", "<red>Booster-Expert",       "+50% Booster-Dauer",     2, "daily_boost",    5);

    // Tier 3 – benötigt 3x Tier-2-Talente + Prestige 25
    public static final Talent INCOME_3       = new Talent("income_3",       "<green>Produktivität III",  "+20% Einkommen",         3, "income_2",       25);
    public static final Talent UPGRADE_COST_3 = new Talent("upgrade_cost_3", "<aqua>Sparsamkeit III",     "-20% Upgrade-Kosten",    3, "upgrade_cost_2", 25);
    public static final Talent TOKEN_MASTERY  = new Talent("token_mastery",  "<gold>Token-Meister",       "Doppelte Token-Drops",   3, "enchant_chance", 25);
    public static final Talent FUSION_COST    = new Talent("fusion_cost",    "<dark_purple>Fusion-Expert","-25% Fusion-Kosten",     3, "synergy_boost",  25);
    public static final Talent GOD_MODE       = new Talent("god_mode",       "<dark_red>Gottgleich ❋",    "+10% Einkomm. -10% Kost",3, "booster_extend", 25);

    public static final Map<String, Talent> ALL_TALENTS = new LinkedHashMap<>();

    static {
        ALL_TALENTS.put(INCOME_1.id(),       INCOME_1);
        ALL_TALENTS.put(UPGRADE_COST_1.id(), UPGRADE_COST_1);
        ALL_TALENTS.put(GENERATOR_SLOT.id(), GENERATOR_SLOT);
        ALL_TALENTS.put(OFFLINE_TIME.id(),   OFFLINE_TIME);
        ALL_TALENTS.put(DAILY_BOOST.id(),    DAILY_BOOST);
        ALL_TALENTS.put(INCOME_2.id(),       INCOME_2);
        ALL_TALENTS.put(UPGRADE_COST_2.id(), UPGRADE_COST_2);
        ALL_TALENTS.put(ENCHANT_CHANCE.id(), ENCHANT_CHANCE);
        ALL_TALENTS.put(SYNERGY_BOOST.id(),  SYNERGY_BOOST);
        ALL_TALENTS.put(BOOSTER_EXTEND.id(), BOOSTER_EXTEND);
        ALL_TALENTS.put(INCOME_3.id(),       INCOME_3);
        ALL_TALENTS.put(UPGRADE_COST_3.id(), UPGRADE_COST_3);
        ALL_TALENTS.put(TOKEN_MASTERY.id(),  TOKEN_MASTERY);
        ALL_TALENTS.put(FUSION_COST.id(),    FUSION_COST);
        ALL_TALENTS.put(GOD_MODE.id(),       GOD_MODE);
    }

    public TalentManager(PHGenerators plugin) {
        this.plugin = plugin;
    }

    /** Lädt Talente aus der DB in PlayerData */
    public void loadTalents(PlayerData data) {
        var talents = plugin.getRepository().loadTalents(data.getUuid());
        data.getUnlockedTalents().clear();
        data.getUnlockedTalents().addAll(talents);
    }

    public enum UnlockResult { SUCCESS, NO_POINTS, ALREADY_UNLOCKED, REQUIREMENTS_NOT_MET, UNKNOWN_TALENT }

    public UnlockResult unlock(Player player, PlayerData data, String talentId) {
        Talent talent = ALL_TALENTS.get(talentId);
        if (talent == null) return UnlockResult.UNKNOWN_TALENT;
        if (data.hasTalent(talentId)) return UnlockResult.ALREADY_UNLOCKED;
        if (data.getTalentPoints() < 1) return UnlockResult.NO_POINTS;

        // Tier-Voraussetzungen prüfen
        if (talent.tier() >= 2 && !meetsT1Requirement(data)) return UnlockResult.REQUIREMENTS_NOT_MET;
        if (talent.tier() >= 3 && !meetsT2Requirement(data)) return UnlockResult.REQUIREMENTS_NOT_MET;
        if (data.getPrestige() < talent.prestigeReq()) return UnlockResult.REQUIREMENTS_NOT_MET;
        if (talent.requiredTalent() != null && !data.hasTalent(talent.requiredTalent()))
            return UnlockResult.REQUIREMENTS_NOT_MET;

        data.unlockTalent(talentId);
        data.setTalentPoints(data.getTalentPoints() - 1);

        plugin.getRepository().saveTalent(data.getUuid(), talentId);
        plugin.getRepository().savePlayer(data);

        player.sendMessage(MM.deserialize(
                "<green>✔ Talent freigeschaltet: " + talent.name() + "\n"
                + "<gray>" + talent.description()
                + "\n<dark_gray>Verbleibende Talentpunkte: <white>" + data.getTalentPoints()));
        return UnlockResult.SUCCESS;
    }

    /** Talentpunkte beim Prestige vergeben */
    public void onPrestige(PlayerData data) {
        data.addTalentPoints(1);
    }

    // ── Talent-Effekte ────────────────────────────────────────────────────────

    public double getIncomeMultiplier(PlayerData data) {
        double mult = 1.0;
        if (data.hasTalent("income_1")) mult += 0.05;
        if (data.hasTalent("income_2")) mult += 0.10;
        if (data.hasTalent("income_3")) mult += 0.20;
        if (data.hasTalent("god_mode")) mult += 0.10;
        return mult;
    }

    public double getUpgradeCostMultiplier(PlayerData data) {
        double mult = 1.0;
        if (data.hasTalent("upgrade_cost_1")) mult -= 0.05;
        if (data.hasTalent("upgrade_cost_2")) mult -= 0.10;
        if (data.hasTalent("upgrade_cost_3")) mult -= 0.20;
        if (data.hasTalent("god_mode"))       mult -= 0.10;
        return Math.max(0.1, mult);
    }

    public int getExtraGeneratorSlots(PlayerData data) {
        return data.hasTalent("generator_slot") ? 1 : 0;
    }

    public int getExtraOfflineHours(PlayerData data) {
        return data.hasTalent("offline_time") ? 1 : 0;
    }

    public double getEnchantChanceBonus(PlayerData data) {
        return data.hasTalent("enchant_chance") ? 0.05 : 0.0;
    }

    public double getSynergyMultiplierBonus(PlayerData data) {
        return data.hasTalent("synergy_boost") ? 1.5 : 1.0;
    }

    public double getBoosterDurationMultiplier(PlayerData data) {
        return data.hasTalent("booster_extend") ? 1.5 : 1.0;
    }

    public double getDailyBonusMultiplier(PlayerData data) {
        return data.hasTalent("daily_boost") ? 1.25 : 1.0;
    }

    public double getFusionCostMultiplier(PlayerData data) {
        return data.hasTalent("fusion_cost") ? 0.75 : 1.0;
    }

    public boolean hasTokenMastery(PlayerData data) {
        return data.hasTalent("token_mastery");
    }

    // ── Tier-Checks ───────────────────────────────────────────────────────────

    private boolean meetsT1Requirement(PlayerData data) {
        long t1Count = ALL_TALENTS.values().stream()
                .filter(t -> t.tier() == 1 && data.hasTalent(t.id())).count();
        return t1Count >= 3;
    }

    private boolean meetsT2Requirement(PlayerData data) {
        long t2Count = ALL_TALENTS.values().stream()
                .filter(t -> t.tier() == 2 && data.hasTalent(t.id())).count();
        return t2Count >= 3;
    }

    // ── Info ──────────────────────────────────────────────────────────────────

    public String getTalentInfo(PlayerData data) {
        StringBuilder sb = new StringBuilder("<gold>━━ Talentbaum ━━\n");
        sb.append("<gray>Talentpunkte: <white>").append(data.getTalentPoints()).append("\n\n");
        int currentTier = 0;
        for (Talent t : ALL_TALENTS.values()) {
            if (t.tier() != currentTier) {
                currentTier = t.tier();
                sb.append("<yellow>── Tier ").append(currentTier).append(" ──\n");
            }
            boolean unlocked = data.hasTalent(t.id());
            String status = unlocked ? "<green>✔ " : "<dark_gray>○ ";
            sb.append(status).append(t.name()).append(" <dark_gray>– ").append(t.description()).append("\n");
        }
        return sb.toString().trim();
    }
}
