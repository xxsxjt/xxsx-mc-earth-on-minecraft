package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reads live lode reserves around a point for the geology hammer and the ore scanner.
 *
 * <p>Every lode block carries a {@link DepositBlockEntity}, so a survey walks the block-entity
 * positions of each chunk instead of scanning a cube block by block. Only chunks that are already
 * loaded are read; a survey never loads or generates terrain.</p>
 */
public final class ProspectingSurvey {
    /** One live lode block. */
    public record Sample(BlockPos pos, String lodeId, int remaining) {
    }

    /** All samples of one lode, with the block nearest to the survey origin. */
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
        for (int cx = center.x() - chunkRadius; cx <= center.x() + chunkRadius; cx++) {
            for (int cz = center.z() - chunkRadius; cz <= center.z() + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    Block block = chunk.getBlockState(pos).getBlock();
                    String lodeId = DepositSpec.lodeIdOf(block);
                    if (lodeId == null || !lodes.test(lodeId)) {
                        continue;
                    }
                    if (chunk.getBlockEntity(pos) instanceof DepositBlockEntity deposit
                            && !deposit.reserve().isEmpty()) {
                        samples.add(new Sample(pos.immutable(), lodeId, deposit.reserve().remaining()));
                    }
                }
            }
        }
        return samples;
    }

    /** Groups samples by lode, nearest group first. */
    public static List<Group> group(List<Sample> samples, BlockPos origin) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Sample sample : samples) {
            double distance = Math.sqrt(sample.pos().distSqr(origin));
            groups.merge(sample.lodeId(),
                    new Group(sample.lodeId(), 1, sample.remaining(), sample.pos(), distance),
                    (a, b) -> {
                        boolean bCloser = b.distance() < a.distance();
                        return new Group(a.lodeId(), a.blocks() + b.blocks(), a.remaining() + b.remaining(),
                                bCloser ? b.nearest() : a.nearest(), Math.min(a.distance(), b.distance()));
                    });
        }
        List<Group> result = new ArrayList<>(groups.values());
        result.sort(Comparator.comparingDouble(Group::distance).thenComparing(Group::lodeId));
        return result;
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
