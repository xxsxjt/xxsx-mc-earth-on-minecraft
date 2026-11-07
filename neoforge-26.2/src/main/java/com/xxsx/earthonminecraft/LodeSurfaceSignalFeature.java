package com.xxsx.earthonminecraft;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Post-ore pass that marks the surface above real ore bodies.
 *
 * <p>Runs once per chunk after {@code underground_ores}. It reads the main-lode blocks already
 * placed in the chunk with {@link LodeSectionScan} (ore features write chunk sections directly and
 * leave no block-entity record), and for every main lode with at least {@link #MIN_BLOCKS} blocks
 * puts a signal at the surface above the lode's XZ centroid: a small patch of its halo rock, or a
 * few outcrop blocks of the lode itself. A signal therefore always sits over ore that exists.</p>
 */
public final class LodeSurfaceSignalFeature extends Feature<NoneFeatureConfiguration> {
    public static final int MIN_BLOCKS = 18;
    public static final int MAX_SIGNALS = 1;
    public static final float SIGNAL_CHANCE = 0.40F;
    public static final int MIN_SURFACE_BLOCKS = 2;
    public static final int MAX_SURFACE_BLOCKS = 3;

    /** A signal to place: which lode, how many blocks backed it, and the surface column. */
    public record Signal(String lodeId, int blocks, int x, int z) {
    }

    public LodeSurfaceSignalFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunkPos = ChunkPos.containing(context.origin());
        ChunkAccess chunk = level.getChunk(chunkPos.x(), chunkPos.z());
        List<LodeSectionScan.Hit> hits = LodeSectionScan.scan(chunk, LodeSectionScan.mainLodeBlocks());
        List<Signal> planned = planSignals(hits);
        RandomSource random = context.random();
        if (planned.isEmpty() || random.nextFloat() >= SIGNAL_CHANCE) {
            return false;
        }
        boolean placed = false;
        for (Signal signal : planned) {
            placed |= placeSignal(level, chunkPos, signal, random);
        }
        return placed;
    }

    /**
     * Picks the signals for one chunk: main lodes only (no halos, no host strata), at least
     * {@link #MIN_BLOCKS} blocks each, largest first, at most {@link #MAX_SIGNALS}.
     */
    public static List<Signal> planSignals(Collection<LodeSectionScan.Hit> hits) {
        return LodeSectionScan.planSignals(hits);
    }

    private static boolean placeSignal(WorldGenLevel level, ChunkPos chunk, Signal signal, RandomSource random) {
        Optional<Block> block = ProspectingCatalog.surfaceSignalFor(signal.lodeId());
        if (block.isEmpty()) {
            return false;
        }
        BlockState state = block.get().defaultBlockState();
        int targetBlocks = MIN_SURFACE_BLOCKS + random.nextInt(MAX_SURFACE_BLOCKS - MIN_SURFACE_BLOCKS + 1);
        int[][] offsets = {
                {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
        };
        for (int i = 1; i < offsets.length; i++) {
            int j = i + random.nextInt(offsets.length - i);
            int[] tmp = offsets[i];
            offsets[i] = offsets[j];
            offsets[j] = tmp;
        }
        int replaced = 0;
        for (int[] offset : offsets) {
            if (replaced >= targetBlocks) {
                break;
            }
            BlockPos top = surface(level, chunk, signal.x() + offset[0], signal.z() + offset[1]);
            if (top != null && replace(level, top, state)) {
                replaced++;
            }
        }
        return replaced > 0;
    }

    /** Top solid block of a column inside this chunk, or {@code null} outside it. */
    private static BlockPos surface(WorldGenLevel level, ChunkPos chunk, int x, int z) {
        if (x < chunk.getMinBlockX() || x > chunk.getMaxBlockX() || z < chunk.getMinBlockZ() || z > chunk.getMaxBlockZ()) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        return y < level.getMinY() ? null : new BlockPos(x, y, z);
    }

    private static boolean replace(WorldGenLevel level, BlockPos pos, BlockState signal) {
        if (pos.getY() < level.getMinY() || !isNaturalSurface(level.getBlockState(pos))) {
            return false;
        }
        return level.setBlock(pos, signal, Block.UPDATE_CLIENTS);
    }

    /**
     * Natural ground the signal may replace; water, ice, snow, wood, leaves and built blocks are left
     * alone. In 26.1 {@code #minecraft:dirt} no longer holds grass, podzol or mycelium, so soil is
     * matched through {@code #minecraft:substrate_overworld} (dirt, mud, moss and grass blocks).
     */
    public static boolean isNaturalSurface(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SUBSTRATE_OVERWORLD)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.DEEPSLATE);
    }
}
