package com.xxsx.earthonminecraft;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps lode blocks to their spec identity and starting reserve size.
 *
 * <p>The numbers come from {@code spec/eom/worldgen.jsonl}: every lode declares
 * {@code lodes_per_region} and {@code reserve_chunks}. A single placed block represents one lode,
 * so its capacity is the per-lode share of the region's reserve, floored at a sane minimum so
 * small deposits are still worth building a machine for.</p>
 */
public final class DepositSpec {
    /** Minimum units any lode holds, so a small body is not pointless to work. */
    private static final int MIN_CAPACITY = 16;

    public record Lode(String id, String blockId, int capacity, String depletedBlockId) {
    }

    private static final Map<Block, Lode> BY_BLOCK = new LinkedHashMap<>();
    private static final Map<String, Lode> BY_ID = new LinkedHashMap<>();
    private static boolean bound;

    private DepositSpec() {
    }

    /**
     * Binds the lode table. Called after blocks are registered, because it needs the block
     * objects to key the lookup.
     */
    public static void bind() {
        if (bound) {
            return;
        }
        bound = true;
        for (SpecDepletedBlocks.Entry entry : SpecDepletedBlocks.entries()) {
            String lodeId = entry.lode();
            Block block = SpecBlocks.BY_ID.containsKey(lodeId)
                    ? SpecBlocks.BY_ID.get(lodeId).get()
                    : null;
            if (block == null) {
                continue;
            }
            var depleted = SpecDepletedBlocks.BY_ID.get(entry.id());
            Lode lode = new Lode(
                    lodeId,
                    lodeId,
                    capacityFor(lodeId),
                    depleted == null ? "" : entry.id());
            BY_BLOCK.put(block, lode);
            BY_ID.put(lodeId, lode);
        }
    }

    /** Capacity in ore units for one lode block. */
    public static int capacityFor(String lodeId) {
        Integer spec = SpecLodeReserves.capacity(lodeId);
        return spec == null ? DepositReserve.DEFAULT_CAPACITY : Math.max(MIN_CAPACITY, spec);
    }

    public static int capacityFor(Identifier lodeId) {
        return capacityFor(lodeId.getPath());
    }

    /** The lode a block belongs to, or a synthetic one so the reserve still works. */
    public static Identifier lodeFor(Block block) {
        Lode lode = BY_BLOCK.get(block);
        if (lode != null) {
            return Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, lode.id());
        }
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "unknown_lode") : id;
    }

    /** The depleted block for a lode id, or {@code null} when the spec names none. */
    public static net.minecraft.world.level.block.state.BlockState byLode(Identifier lodeId) {
        Lode lode = BY_ID.get(lodeId.getPath());
        if (lode == null || lode.depletedBlockId().isEmpty()) {
            return null;
        }
        var holder = SpecDepletedBlocks.BY_ID.get(lode.depletedBlockId());
        return holder == null ? null : holder.get().defaultBlockState();
    }

    public static boolean isLode(Block block) {
        return BY_BLOCK.containsKey(block);
    }

    public static java.util.Collection<Lode> lodes() {
        return BY_ID.values();
    }
}
