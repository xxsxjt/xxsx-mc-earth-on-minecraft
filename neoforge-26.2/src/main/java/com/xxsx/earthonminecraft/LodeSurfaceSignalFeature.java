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
    public static final int MIN_BLOCKS = 6;
    public static final int MAX_SIGNALS = 3;
    private static final int HALO_RADIUS = 2;
    private static final float HALO_SKIP_CHANCE = 0.3F;

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
        RandomSource random = context.random();
        boolean placed = false;
        for (Signal signal : planSignals(hits)) {
            placed |= placeSignal(level, chunkPos, signal, random);
        }
        return placed;
    }

    /**
     * Picks the signals for one chunk: main lodes only (no halos, no host strata), at least
     * {@link #MIN_BLOCKS} blocks each, largest first, at most {@link #MAX_SIGNALS}.
     */
    public static List<Signal> planSignals(Collection<LodeSectionScan.Hit> hits) {
        Map<String, long[]> totals = new LinkedHashMap<>();
        for (LodeSectionScan.Hit hit : hits) {
            if (!ProspectingCatalog.isMainLode(hit.lodeId())) {
                continue;
            }
            long[] total = totals.computeIfAbsent(hit.lodeId(), id -> new long[3]);
            total[0]++;
            total[1] += hit.pos().getX();
            total[2] += hit.pos().getZ();
        }
        List<Signal> signals = new ArrayList<>();
        totals.forEach((lodeId, total) -> {
            if (total[0] >= MIN_BLOCKS) {
                signals.add(new Signal(lodeId, (int) total[0],
                        (int) Math.floor((double) total[1] / total[0]),
                        (int) Math.floor((double) total[2] / total[0])));
            }
        });
        signals.sort(Comparator.comparingInt(Signal::blocks).reversed().thenComparing(Signal::lodeId));
        return signals.size() > MAX_SIGNALS ? new ArrayList<>(signals.subList(0, MAX_SIGNALS)) : signals;
    }

    private static boolean placeSignal(WorldGenLevel level, ChunkPos chunk, Signal signal, RandomSource random) {
        Optional<Block> block = ProspectingCatalog.surfaceSignalFor(signal.lodeId());
        if (block.isEmpty()) {
            return false;
        }
        BlockState state = block.get().defaultBlockState();
        boolean placed = false;
        if (ProspectingCatalog.signalsWithHalo(signal.lodeId())) {
            for (int dx = -HALO_RADIUS; dx <= HALO_RADIUS; dx++) {
                for (int dz = -HALO_RADIUS; dz <= HALO_RADIUS; dz++) {
                    if (dx * dx + dz * dz > HALO_RADIUS * HALO_RADIUS + 1 || random.nextFloat() < HALO_SKIP_CHANCE) {
                        continue;
                    }
                    BlockPos top = surface(level, chunk, signal.x() + dx, signal.z() + dz);
                    if (top == null) {
                        continue;
                    }
                    boolean topReplaced = replace(level, top, state);
                    placed |= topReplaced;
                    if (random.nextBoolean() && topReplaced) {
                        placed |= replace(level, top.below(), state);
                    }
                }
            }
        } else {
            int count = 1 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                int dx = i == 0 ? 0 : random.nextInt(3) - 1;
                int dz = i == 0 ? 0 : random.nextInt(3) - 1;
                BlockPos top = surface(level, chunk, signal.x() + dx, signal.z() + dz);
                if (top != null) {
                    placed |= replace(level, top, state);
                }
            }
        }
        return placed;
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
