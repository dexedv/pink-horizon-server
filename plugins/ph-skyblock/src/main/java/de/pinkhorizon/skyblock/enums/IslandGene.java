package de.pinkhorizon.skyblock.enums;

/**
 * Gene die eine Insel bei der Erstellung zufällig erhält.
 * Jede Insel bekommt 5 zufällige Gene aus diesem Pool.
 */
public enum IslandGene {

    FRUITFUL      ("Fruchtbar",        "<green>Crop-Yield +20%"),
    MAGNETIC      ("Magnetisch",       "<aqua>Items im 5-Block-Radius automatisch aufsammeln"),
    VOLCANIC      ("Vulkanisch",       "<red>Cobblestone-Generator: 5% Chance auf seltene Ores"),
    ENCHANTED     ("Verzaubert",       "<light_purple>Enchantment-Kosten -25%"),
    ABYSSAL       ("Abyssisch",        "<dark_aqua>Void-Fishing: Loot-Qualität 2x"),
    SUNNY         ("Sonnig",           "<yellow>Maschinen-Solarenergie 2x"),
    STONE_RICH    ("Steinreich",       "<gray>Mining-XP +30%"),
    TRADER_LUCK   ("Händlerglück",     "<gold>Shop-Verkaufspreise +15%"),
    FORTRESS      ("Bergfried",        "<dark_red>Mob-Drop-Chance +20%"),
    TIME_WARPED   ("Zeitgewirrt",      "<blue>Pflanzen wachsen 2x schneller"),
    SCIENTIFIC    ("Wissenschaftlich", "<white>Forschungszeit -20%"),
    STARBOUND     ("Sterngebunden",    "<yellow>Sternschnuppen landen häufiger auf dieser Insel"),
    VOID_TOUCHED  ("Void-Berührt",     "<dark_purple>Void-Fishing: +1 Loot-Roll"),
    ANCIENT       ("Uralt",            "<gold>+10% XP aus allen Quellen"),
    CRYSTALLINE   ("Kristallin",       "<aqua>Runen erhalten +1 Slot"),
    LUCKY         ("Glücklich",        "<yellow>+5% Chance auf Doppel-Drops"),
    SWIFT         ("Wendig",           "<green>Bewegungsgeschwindigkeit +10% auf Insel"),
    MYSTICAL      ("Mystisch",         "<light_purple>Ritual-Cooldowns -25%"),
    WEALTHY       ("Wohlhabend",       "<gold>Start-Coins +1.000 beim Prestige"),
    ENERGIZED     ("Energetisch",      "<yellow>Maschinen-Energieverbrauch -15%");

    public final String displayName;
    public final String description;

    IslandGene(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
