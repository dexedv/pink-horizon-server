package de.pinkhorizon.minigames;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class VoidGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // Leere Welt – kein Block wird gesetzt
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random,
                                int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // Nichts
    }

    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random,
                                int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // Kein Bedrock
    }

    @Override
    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // Keine Höhlen
    }
}
