package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds lode blocks in a chunk by reading its block states section by section.
 *
 * <p>Lode blocks cannot be found through block-entity records: vanilla {@code OreFeature} writes
 * straight into {@link LevelChunkSection} and leaves no pending block-entity entry, and
 * {@link DepositBlockEntity} is only created on first use. Each section's palette is checked first,
 * so sections that hold none of the target blocks cost almost nothing.</p>
 */
public final class LodeSectionScan {
    /** A live lode block found in a chunk. */
    public record Hit(BlockPos pos, String lodeId) {
    }

    private static volatile Set<Block> mainLodeBlocks;

    private LodeSectionScan() {
    }

    /** Live blocks of every lode accepted by {@code lodes}. */
    public static Set<Block> blocksFor(Predicate<String> lodes) {
        Set<Block> blocks = new HashSet<>();
        for (DepositSpec.Lode lode : DepositSpec.lodes()) {
            if (lodes.test(lode.id())) {
                Block block = DepositSpec.blockOf(lode.id());
                if (block != null) {
                    blocks.add(block);
                }
            }
        }
        return Set.copyOf(blocks);
    }

    /**
     * Blocks of the main ore bodies, the only ones a surface signal is planned for. Cached once the
     * lode table is bound in common setup; an empty result is not cached.
     */
    public static Set<Block> mainLodeBlocks() {
        Set<Block> blocks = mainLodeBlocks;
        if (blocks == null) {
            blocks = blocksFor(ProspectingCatalog::isMainLode);
            if (!blocks.isEmpty()) {
                mainLodeBlocks = blocks;
            }
        }
        return blocks;
    }

    /**
     * Picks the surface signals for one chunk: main lodes only (no halos, no host strata), at least
     * {@link LodeSurfaceSignalFeature#MIN_BLOCKS} blocks each, largest first, at most
     * {@link LodeSurfaceSignalFeature#MAX_SIGNALS}.
     */
    public static List<LodeSurfaceSignalFeature.Signal> planSignals(Collection<Hit> hits) {
        Map<String, long[]> totals = new LinkedHashMap<>();
        for (Hit hit : hits) {
            if (!ProspectingCatalog.isMainLode(hit.lodeId())) {
                continue;
            }
            long[] total = totals.computeIfAbsent(hit.lodeId(), id -> new long[3]);
            total[0]++;
            total[1] += hit.pos().getX();
            total[2] += hit.pos().getZ();
        }
        List<LodeSurfaceSignalFeature.Signal> signals = new ArrayList<>();
        totals.forEach((lodeId, total) -> {
            if (total[0] >= LodeSurfaceSignalFeature.MIN_BLOCKS) {
                signals.add(new LodeSurfaceSignalFeature.Signal(lodeId, (int) total[0],
                        (int) Math.floor((double) total[1] / total[0]),
                        (int) Math.floor((double) total[2] / total[0])));
            }
        });
        signals.sort(Comparator.comparingInt(LodeSurfaceSignalFeature.Signal::blocks)
                .reversed()
                .thenComparing(LodeSurfaceSignalFeature.Signal::lodeId));
        return signals.size() > LodeSurfaceSignalFeature.MAX_SIGNALS
                ? new ArrayList<>(signals.subList(0, LodeSurfaceSignalFeature.MAX_SIGNALS))
                : signals;
    }

    /** Every block of {@code targets} in {@code chunk}, bottom section first. */
    public static List<Hit> scan(ChunkAccess chunk, Set<Block> targets) {
        List<Hit> hits = new ArrayList<>();
        if (targets.isEmpty()) {
            return hits;
        }
        Predicate<BlockState> isTarget = state -> targets.contains(state.getBlock());
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(isTarget)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
                for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                    for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
                        Block block = section.getBlockState(x, y, z).getBlock();
                        if (!targets.contains(block)) {
                            continue;
                        }
                        String lodeId = DepositSpec.lodeIdOf(block);
                        if (lodeId != null) {
                            hits.add(new Hit(new BlockPos(baseX + x, baseY + y, baseZ + z), lodeId));
                        }
                    }
                }
            }
        }
        return hits;
    }
}
