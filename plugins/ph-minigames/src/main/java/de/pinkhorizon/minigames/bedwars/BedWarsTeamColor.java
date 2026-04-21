package de.pinkhorizon.minigames.bedwars;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;

public enum BedWarsTeamColor {

    RED   ("§cRot",    "§c", Material.RED_BED,    Material.RED_WOOL,    Material.RED_TERRACOTTA,    DyeColor.RED,    Color.RED),
    BLUE  ("§9Blau",   "§9", Material.BLUE_BED,   Material.BLUE_WOOL,   Material.BLUE_TERRACOTTA,   DyeColor.BLUE,   Color.BLUE),
    GREEN ("§aGrün",   "§a", Material.GREEN_BED,  Material.GREEN_WOOL,  Material.GREEN_TERRACOTTA,  DyeColor.GREEN,  Color.GREEN),
    YELLOW("§eGelb",   "§e", Material.YELLOW_BED, Material.YELLOW_WOOL, Material.YELLOW_TERRACOTTA, DyeColor.YELLOW, Color.YELLOW);

    public final String displayName;
    public final String chatColor;
    public final Material bedMaterial;
    public final Material woolMaterial;
    public final Material terracottaMaterial;
    public final DyeColor dyeColor;
    public final Color armorColor;

    BedWarsTeamColor(String displayName, String chatColor,
                     Material bedMaterial, Material woolMaterial, Material terracottaMaterial,
                     DyeColor dyeColor, Color armorColor) {
        this.displayName        = displayName;
        this.chatColor          = chatColor;
        this.bedMaterial        = bedMaterial;
        this.woolMaterial       = woolMaterial;
        this.terracottaMaterial = terracottaMaterial;
        this.dyeColor           = dyeColor;
        this.armorColor         = armorColor;
    }

    public static BedWarsTeamColor fromString(String name) {
        for (BedWarsTeamColor c : values()) {
            if (c.name().equalsIgnoreCase(name)) return c;
        }
        return null;
    }
}
