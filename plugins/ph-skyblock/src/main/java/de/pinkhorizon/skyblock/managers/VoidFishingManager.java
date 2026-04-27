package de.pinkhorizon.skyblock.managers;

import de.pinkhorizon.skyblock.PHSkyBlock;
import de.pinkhorizon.skyblock.enums.IslandGene;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Verwaltet Void-Fishing: Angeln unter Y=0 mit eigener Loot-Tabelle.
 */
public class VoidFishingManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum VoidLootTier { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

    private record VoidLoot(String id, String name, String color, Material icon, VoidLootTier tier) {}

    // Loot-Tabelle
    private static final List<VoidLoot> LOOT_TABLE = List.of(
        new VoidLoot("void_bone",       "Void-Knochen",        "<gray>",         Material.BONE,              VoidLootTier.COMMON),
        new VoidLoot("void_grate",      "Void-Gräten",         "<dark_gray>",    Material.BONE_MEAL,         VoidLootTier.COMMON),
        new VoidLoot("dark_ink",        "Dunkle Tinte",        "<gray>",         Material.INK_SAC,           VoidLootTier.COMMON),
        new VoidLoot("deep_stone",      "Tiefenstein",         "<dark_gray>",    Material.DEEPSLATE,         VoidLootTier.COMMON),
        new VoidLoot("void_sand",       "Void-Sand",           "<gray>",         Material.SOUL_SAND,         VoidLootTier.COMMON),
        new VoidLoot("void_pearl",      "Void-Perle",          "<dark_aqua>",    Material.ENDER_PEARL,       VoidLootTier.UNCOMMON),
        new VoidLoot("trident_frag",    "Dreizack-Fragment",   "<aqua>",         Material.PRISMARINE_SHARD,  VoidLootTier.UNCOMMON),
        new VoidLoot("mutant_kelp",     "Mutanter Seetang",    "<green>",        Material.KELP,              VoidLootTier.UNCOMMON),
        new VoidLoot("deep_lantern",    "Tiefseelaterne",      "<dark_aqua>",    Material.SEA_LANTERN,       VoidLootTier.UNCOMMON),
        new VoidLoot("void_coral",      "Void-Koralle",        "<aqua>",         Material.TUBE_CORAL,        VoidLootTier.UNCOMMON),
        new VoidLoot("abyssal_crystal", "Abyssaler Kristall",  "<light_purple>", Material.AMETHYST_SHARD,    VoidLootTier.RARE),
        new VoidLoot("ancient_coin",    "Antike Münze",        "<gold>",         Material.GOLD_NUGGET,       VoidLootTier.RARE),
        new VoidLoot("deep_lantern2",   "Urzeitfossil",        "<yellow>",       Material.BONE,              VoidLootTier.RARE),
        new VoidLoot("void_scale",      "Void-Schuppe",        "<dark_purple>",  Material.RABBIT_HIDE,       VoidLootTier.RARE),
        new VoidLoot("depth_compass",   "Tiefen-Kompass",      "<aqua>",         Material.COMPASS,           VoidLootTier.RARE),
        new VoidLoot("void_staff",      "Void-Stab",           "<light_purple>", Material.BLAZE_ROD,         VoidLootTier.EPIC),
        new VoidLoot("abyssal_egg",     "Abyssales Ei",        "<dark_purple>",  Material.DRAGON_EGG,        VoidLootTier.EPIC),
        new VoidLoot("leviathan_scale", "Leviathan-Schuppe",   "<gold>",         Material.NETHERITE_SCRAP,   VoidLootTier.LEGENDARY),
        new VoidLoot("void_heart",      "Void-Herz",           "<dark_purple>",  Material.NETHER_STAR,       VoidLootTier.LEGENDARY)
    );

    // Gewichtungen pro Tier
    private static final Map<VoidLootTier, Integer> WEIGHTS = Map.of(
        VoidLootTier.COMMON,    50,
        VoidLootTier.UNCOMMON,  30,
        VoidLootTier.RARE,      15,
        VoidLootTier.EPIC,       4,
        VoidLootTier.LEGENDARY,  1
    );

    private final PHSkyBlock plugin;
    private final Random rng = new Random();
    private final NamespacedKey VOID_ITEM_KEY;

    public VoidFishingManager(PHSkyBlock plugin) {
        this.plugin = plugin;
        VOID_ITEM_KEY = new NamespacedKey(plugin, "void_item_id");
    }

    /**
     * Wird aufgerufen wenn ein Spieler in der Leere angelt (Y < 0).
     * Gibt das Loot-Item zurück.
     */
    public ItemStack rollLoot(Player player) {
        // Gen-Bonus: Abyssisch → 2 Rolls, bestes nehmen
        boolean abyssal = plugin.getIslandDnaManager().playerHasGene(
            player.getUniqueId(), IslandGene.ABYSSAL);
        boolean voidTouched = plugin.getIslandDnaManager().playerHasGene(
            player.getUniqueId(), IslandGene.VOID_TOUCHED);

        int rolls = 1;
        if (abyssal)     rolls++;
        if (voidTouched) rolls++;

        VoidLoot best = null;
        for (int i = 0; i < rolls; i++) {
            VoidLoot candidate = rollOnce();
            if (best == null || candidate.tier().ordinal() > best.tier().ordinal()) {
                best = candidate;
            }
        }
        return buildItem(best);
    }

    private VoidLoot rollOnce() {
        // Tier auswählen
        int total = WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();
        int roll  = rng.nextInt(total);
        int cum   = 0;
        VoidLootTier chosenTier = VoidLootTier.COMMON;
        for (var entry : WEIGHTS.entrySet()) {
            cum += entry.getValue();
            if (roll < cum) { chosenTier = entry.getKey(); break; }
        }

        // Item aus Tier wählen
        VoidLootTier finalTier = chosenTier;
        List<VoidLoot> candidates = LOOT_TABLE.stream()
            .filter(l -> l.tier() == finalTier).toList();
        return candidates.get(rng.nextInt(candidates.size()));
    }

    private ItemStack buildItem(VoidLoot loot) {
        ItemStack item = new ItemStack(loot.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(loot.color() + "<bold>" + loot.name()));
        meta.lore(List.of(
            MM.deserialize("<dark_gray>Void-Fund"),
            MM.deserialize(tierColor(loot.tier()) + "◆ " + loot.tier().name())
        ));
        meta.getPersistentDataContainer().set(VOID_ITEM_KEY, PersistentDataType.STRING, loot.id());
        if (loot.tier() == VoidLootTier.LEGENDARY || loot.tier() == VoidLootTier.EPIC) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String tierColor(VoidLootTier tier) {
        return switch (tier) {
            case COMMON    -> "<gray>";
            case UNCOMMON  -> "<green>";
            case RARE      -> "<aqua>";
            case EPIC      -> "<light_purple>";
            case LEGENDARY -> "<gold>";
        };
    }

    /** Ankündigung bei Legendary-Fang */
    public void announceLegendary(Player player, String itemName) {
        String msg = "<gold>⭐ <yellow>" + player.getName()
            + " <gold>hat ein <bold>LEGENDÄRES</bold> Void-Item gefangen: <white>" + itemName + "<gold>! ⭐";
        plugin.getServer().getOnlinePlayers().forEach(p ->
            p.sendMessage(MM.deserialize(msg)));
    }

    /** Erstellt einen Void-Stab (für Shop) */
    public ItemStack createVoidRod() {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.displayName(MM.deserialize("<dark_purple><bold>Void-Angelrute"));
        meta.lore(List.of(
            MM.deserialize("<gray>Angle in der Leere (Y < 0)"),
            MM.deserialize("<dark_aqua>+10% Rare-Chance"),
            MM.deserialize("<dark_gray>Void-Ausrüstung")
        ));
        meta.getPersistentDataContainer().set(VOID_ITEM_KEY, PersistentDataType.STRING, "void_rod");
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 2, true);
        rod.setItemMeta(meta);
        return rod;
    }
}
