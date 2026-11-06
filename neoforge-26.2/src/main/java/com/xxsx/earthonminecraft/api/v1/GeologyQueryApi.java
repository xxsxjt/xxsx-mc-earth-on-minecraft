package com.xxsx.earthonminecraft.api.v1;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;

public interface GeologyQueryApi {
    Optional<GeologySample> inspect(LevelReader level, BlockPos pos);

    Optional<GeologySample> describe(Identifier blockId);

    /**
     * Queries live mineral deposit outlines in loaded chunks within {@code radiusBlocks} of {@code center},
     * intended for remote-sensing instruments (e.g. S-049 satellite survey). Never generates or loads chunks.
     */
    default List<RemoteDepositOutline> queryDeposits(LevelReader level, BlockPos center, int radiusBlocks) {
        return List.of();
    }

    enum DepositRole {
        ORE,
        VEIN,
        SEAM,
        BED,
        PLACER,
        LATERITE,
        CARBONATITE,
        ROCK,
        SOIL,
        OTHER
    }

    record GeologySample(Identifier blockId, DepositRole role, String formula, String categoryKey,
                         String sourceKey, String processKey, boolean earthNaturalSource) {
    }

    record RemoteDepositOutline(String lodeId, Identifier representativeBlockId, BlockPos nearestPos,
                                int blockCount, int estimatedReserve, double horizontalDistance) {
    }
}
