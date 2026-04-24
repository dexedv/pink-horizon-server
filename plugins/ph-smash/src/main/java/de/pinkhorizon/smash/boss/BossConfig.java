package de.pinkhorizon.smash.boss;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record BossConfig(int level, double maxHp, double damage, String displayName) {

    private static final List<String> MYTH_NAMES = List.of(
        // Olympische Götter
        "Zeus", "Hera", "Poseidon", "Demeter", "Athena", "Apollo", "Artemis",
        "Ares", "Aphrodite", "Hephaistos", "Hermes", "Dionysos", "Hades",
        "Persephone", "Hestia",
        // Titanen
        "Kronos", "Rhea", "Okeanos", "Hyperion", "Prometheus", "Epimetheus",
        "Atlas", "Iapetos", "Koios", "Phoibe", "Mnemosyne", "Themis",
        // Helden
        "Herakles", "Perseus", "Theseus", "Achilleus", "Odysseus", "Jason",
        "Orpheus", "Bellerophon", "Meleager", "Atalante", "Peleus", "Kadmos",
        "Aineias", "Orion", "Bellerophontes",
        // Monster & Dämonen
        "Medusa", "Typhon", "Echidna", "Sphinx", "Chimaira", "Skylla",
        "Charybdis", "Minotauros", "Kerberos", "Hydra",
        // Weitere Gottheiten
        "Hekate", "Morpheus", "Thanatos", "Hypnos", "Nemesis", "Nike",
        "Eros", "Helios", "Selene", "Eos", "Kratos", "Bia", "Iris",
        "Nyx", "Erebos", "Phobos", "Deimos", "Eris", "Alekto", "Megaira",
        // Sterbliche & Halbgötter
        "Ikaros", "Daidalos", "Midas", "Sisyphos", "Tantalos", "Kirke",
        "Medeia", "Narziß", "Minos", "Teiresias", "Kalypso", "Narkissos"
    );

    public static BossConfig forLevel(int level) {
        double hp     = Math.max(100, Math.round(Math.pow(level, 3.0) / 7.0));
        double damage = 4.0 + level * 0.8;

        String tier;
        String nameColor;
        if      (level >= 500) { tier = "§4§lLEGENDÄR"; nameColor = "§4"; }
        else if (level >= 100) { tier = "§5§lEPIC";      nameColor = "§5"; }
        else if (level >= 50)  { tier = "§6§lSELTEN";    nameColor = "§6"; }
        else                   { tier = "§c§lBoss";       nameColor = "§c"; }

        String mythName = MYTH_NAMES.get(ThreadLocalRandom.current().nextInt(MYTH_NAMES.size()));
        String name = tier + " " + nameColor + mythName + " §8[Lv. §c" + level + "§8]";
        return new BossConfig(level, hp, damage, name);
    }

    /** Formatiert HP-Zahl lesbar: 1.174.313 → "1,17M" */
    public String formatHp(double hp) {
        if (hp >= 1_000_000_000) return String.format("%.2fB", hp / 1_000_000_000);
        if (hp >= 1_000_000)     return String.format("%.2fM", hp / 1_000_000);
        if (hp >= 1_000)         return String.format("%.1fK", hp / 1_000);
        return String.format("%.0f", hp);
    }
}
