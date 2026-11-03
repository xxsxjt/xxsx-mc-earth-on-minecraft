package com.xxsx.earthonminecraft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A finite ore body attached to a lode block by its block entity.
 *
 * <p>The spec models every lode with {@code depletes: true}, a {@code depleted_block} and a
 * {@code releach_efficiency}. This class carries that state at runtime: a lode starts with a
 * reserve sized from {@code lodes_per_region} / {@code reserve_chunks}, yields its loot while the
 * reserve lasts, and converts to the depleted block when it runs dry. The depleted form remains
 * as visible host rock and can be removed by the bounded quarry.</p>
 */
public final class DepositReserve {
    /** Units of ore a fresh lode holds when the spec gives no better number. */
    public static final int DEFAULT_CAPACITY = 64;

    public static final Codec<DepositReserve> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("lode").forGetter(DepositReserve::lodeId),
            Codec.INT.fieldOf("remaining").forGetter(DepositReserve::remaining),
            Codec.INT.fieldOf("capacity").forGetter(DepositReserve::capacity)
    ).apply(instance, DepositReserve::new));

    private final Identifier lodeId;
    private int remaining;
    private final int capacity;

    public DepositReserve(Identifier lodeId, int remaining, int capacity) {
        this.lodeId = lodeId;
        this.capacity = Math.max(1, capacity);
        this.remaining = Math.max(0, Math.min(remaining, this.capacity));
    }

    public static DepositReserve fresh(Identifier lodeId, int capacity) {
        return new DepositReserve(lodeId, capacity, capacity);
    }

    public Identifier lodeId() {
        return lodeId;
    }

    public int remaining() {
        return remaining;
    }

    public int capacity() {
        return capacity;
    }

    public boolean isEmpty() {
        return remaining <= 0;
    }

    public float fraction() {
        return capacity <= 0 ? 0.0F : (float) remaining / capacity;
    }

    /**
     * Takes up to {@code units} out of the reserve, returning how many were actually taken. The
     * caller decides what items that corresponds to.
     */
    public int extract(int units) {
        if (units <= 0 || remaining <= 0) {
            return 0;
        }
        int taken = Math.min(units, remaining);
        remaining -= taken;
        return taken;
    }

    public DepositReserve copy() {
        return new DepositReserve(lodeId, remaining, capacity);
    }

    /**
     * Rolls the loot table of {@code state}'s block as if it had been mined normally. A machine
     * therefore produces exactly what a player with a pickaxe would have got.
     */
    public static List<ItemStack> rollLoot(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                            BlockState state) {
        return Block.getDrops(state, level, pos, null);
    }

    public static List<ItemStack> rollLoot(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                            BlockState state, ItemStack tool) {
        return Block.getDrops(state, level, pos, null, null, tool);
    }
}
