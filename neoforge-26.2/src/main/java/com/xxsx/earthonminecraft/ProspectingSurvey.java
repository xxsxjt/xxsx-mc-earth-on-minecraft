package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads live lode reserves around a point for the geology hammer and the ore scanner.
 *
 * <p>Worldgen ore blocks carry no block entity until something touches them, so a survey reads
 * block states through {@link LodeSectionScan}. A block whose {@link DepositBlockEntity} already
 * exists reports its saved reserve; any other block counts at full capacity, and no block entity
 * is created just to read it. Only chunks that are already loaded are read; a survey never loads
 * or generates terrain.</p>
 */
public final class ProspectingSurvey {
    /**
     * One live lode block. {@code surface} marks a block in the top two layers of its column, such
     * as an outcrop signal, which says nothing about where the ore body lies.
     */
    public record Sample(BlockPos pos, String lodeId, int remaining, boolean surface) {
    }

    /**
     * All samples of one lode. {@code nearest} is the closest buried block, or the closest surface
     * block when the lode has no buried block in range; {@code distance} is measured to it.
     */
    public record Group(String lodeId, int blocks, int remaining, BlockPos nearest, double distance) {
    }

    /** Eight compass points with -Z as north, plus {@code HERE} when the target is overhead or underfoot. */
    public enum Bearing {
        NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST, HERE;

        public String translationKey() {
            return "message.earth_on_minecraft.prospect.direction." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private ProspectingSurvey() {
    }

    /**
     * Live lode samples in the (2r+1)x(2r+1) chunks around {@code center}, over the full world
     * height. Lodes rejected by {@code lodes} and empty reserves are skipped.
     */
    public static List<Sample> collect(Level level, ChunkPos center, int chunkRadius, Predicate<String> lodes) {
        List<Sample> samples = new ArrayList<>();
        Set<Block> targets = LodeSectionScan.blocksFor(lodes);
        if (targets.isEmpty()) {
            return samples;
        }
        for (int cx = center.x() - chunkRadius; cx <= center.x() + chunkRadius; cx++) {
            for (int cz = center.z() - chunkRadius; cz <= center.z() + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (LodeSectionScan.Hit hit : LodeSectionScan.scan(chunk, targets)) {
                    int remaining = remainingAt(chunk, hit);
                    if (remaining <= 0) {
                        continue;
                    }
                    BlockPos pos = hit.pos();
                    boolean surface = pos.getY()
                            >= chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ()) - 1;
                    samples.add(new Sample(pos, hit.lodeId(), remaining, surface));
                }
            }
        }
        return samples;
    }

    /**
     * Reserve left in one lode block. {@link LevelChunk#getBlockEntity(BlockPos)} only checks for an
     * existing entity (and promotes saved data); it never builds a new one, so an untouched block
     * reads as full.
     */
    private static int remainingAt(LevelChunk chunk, LodeSectionScan.Hit hit) {
        BlockEntity entity = chunk.getBlockEntity(hit.pos());
        if (entity instanceof DepositBlockEntity deposit) {
            return deposit.reserve().remaining();
        }
        return DepositSpec.capacityFor(hit.lodeId());
    }

    /**
     * Groups samples by lode, nearest group first. Every sample counts toward blocks and reserve;
     * the reported point prefers buried blocks so an outcrop does not hide the body beneath it.
     */
    public static List<Group> group(List<Sample> samples, BlockPos origin) {
        Map<String, Accumulator> groups = new LinkedHashMap<>();
        for (Sample sample : samples) {
            groups.computeIfAbsent(sample.lodeId(), Accumulator::new).add(sample, origin);
        }
        List<Group> result = new ArrayList<>();
        groups.values().forEach(accumulator -> result.add(accumulator.toGroup()));
        result.sort(Comparator.comparingDouble(Group::distance).thenComparing(Group::lodeId));
        return result;
    }

    private static final class Accumulator {
        private final String lodeId;
        private int blocks;
        private int remaining;
        private BlockPos buried;
        private double buriedDistance = Double.MAX_VALUE;
        private BlockPos exposed;
        private double exposedDistance = Double.MAX_VALUE;

        Accumulator(String lodeId) {
            this.lodeId = lodeId;
        }

        void add(Sample sample, BlockPos origin) {
            blocks++;
            remaining += sample.remaining();
            double distance = Math.sqrt(sample.pos().distSqr(origin));
            if (sample.surface()) {
                if (distance < exposedDistance) {
                    exposed = sample.pos();
                    exposedDistance = distance;
                }
            } else if (distance < buriedDistance) {
                buried = sample.pos();
                buriedDistance = distance;
            }
        }

        Group toGroup() {
            return buried != null
                    ? new Group(lodeId, blocks, remaining, buried, buriedDistance)
                    : new Group(lodeId, blocks, remaining, exposed, exposedDistance);
        }
    }

    /** Rounds a block distance to the nearest multiple of five. */
    public static int roundToFive(double value) {
        return (int) Math.round(value / 5.0) * 5;
    }

    public static double horizontalDistance(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Compass bearing from {@code from} to {@code to}; {@code HERE} when the rounded distance is zero. */
    public static Bearing bearing(BlockPos from, BlockPos to) {
        if (roundToFive(horizontalDistance(from, to)) == 0) {
            return Bearing.HERE;
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        if (degrees < 0) {
            degrees += 360.0;
        }
        int sector = (int) Math.round(degrees / 45.0) % 8;
        return Bearing.values()[sector];
    }

    /** Height of {@code to} relative to {@code from}, rounded to five; negative means deeper. */
    public static int roundedHeight(BlockPos from, BlockPos to) {
        int dy = to.getY() - from.getY();
        return Integer.signum(dy) * roundToFive(Math.abs(dy));
    }

    /** Player-facing lode name: the translated name of the lode's block. */
    public static Component lodeName(String lodeId) {
        Block block = DepositSpec.blockOf(lodeId);
        return block == null
                ? Component.translatable("message.earth_on_minecraft.prospect.unknown_lode")
                : block.getName();
    }

    /** "about N blocks down", "about N blocks up" or "at this level". */
    public static Component heightText(int roundedHeight) {
        if (roundedHeight < 0) {
            return Component.translatable("message.earth_on_minecraft.prospect.below", -roundedHeight);
        }
        if (roundedHeight > 0) {
            return Component.translatable("message.earth_on_minecraft.prospect.above", roundedHeight);
        }
        return Component.translatable("message.earth_on_minecraft.prospect.level");
    }
}
