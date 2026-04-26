package de.pinkhorizon.generators.managers;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Chunk-Generator der leere (Void) Chunks erzeugt.
 * Wird für Spieler-Inseln genutzt damit Chunks außerhalb der
 * kopierten Island-Template-Chunks nicht mit Terrain gefüllt werden.
 */
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Nichts generieren → leerer Chunk (Void)
    }

    @Override
    public boolean shouldGenerateNoise() { return false; }

    @Override
    public boolean shouldGenerateSurface() { return false; }

    @Override
    public boolean shouldGenerateCaves() { return false; }

    @Override
    public boolean shouldGenerateDecorations() { return false; }

    @Override
    public boolean shouldGenerateMobs() { return false; }

    @Override
    public boolean shouldGenerateStructures() { return false; }
}
