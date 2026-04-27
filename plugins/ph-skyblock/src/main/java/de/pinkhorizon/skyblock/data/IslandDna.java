package de.pinkhorizon.skyblock.data;

import de.pinkhorizon.skyblock.enums.IslandGene;
import java.util.List;
import java.util.UUID;

public record IslandDna(UUID islandUuid, List<IslandGene> genes, int combinationsUsed) {

    public boolean hasGene(IslandGene gene) {
        return genes.contains(gene);
    }

    public boolean canCombine() {
        return combinationsUsed < 3;
    }
}
