package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Shared support rules for physical infrastructure and utility routing. */
public final class RealitySupportRules {
    private static final int MAX_SUPPORT_SCAN = 96;

    private RealitySupportRules() {
    }

    public static boolean canPlace(LevelReader level, BlockPos pos) {
        return hasSupportPath(level, pos);
    }

    public static boolean hasSupportPath(LevelReader level, BlockPos origin) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty() && visited.size() < MAX_SUPPORT_SCAN) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }
            if (hasSolidFoundation(level, pos) || hasWallMount(level, pos)) {
                return true;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!visited.contains(next) && isInfrastructure(level.getBlockState(next))) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    public static boolean hasSolidFoundation(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    public static boolean hasWallMount(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (level.getBlockState(neighbor).isFaceSturdy(level, neighbor, direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUtilityConnected(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (isCable(neighbor) || isPowerEndpoint(neighbor)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOperational(LevelReader level, BlockPos pos) {
        return canPlace(level, pos);
    }

    public static boolean isCableOperational(LevelReader level, BlockPos pos) {
        return canPlace(level, pos) && isUtilityConnected(level, pos);
    }

    public static boolean isInfrastructure(BlockState state) {
        return state.getBlock() instanceof ProcessingMachineBlock
                || state.getBlock() instanceof EnergyGeneratorBlock
                || state.getBlock() instanceof BatteryBoxBlock
                || state.getBlock() instanceof ConveyorBeltBlock
                || state.getBlock() instanceof PowerCableBlock
                || state.getBlock() instanceof SupportPartBlock;
    }

    private static boolean isCable(BlockState state) {
        return state.getBlock() instanceof PowerCableBlock;
    }

    private static boolean isPowerEndpoint(BlockState state) {
        return state.getBlock() instanceof EnergyGeneratorBlock
                || state.getBlock() instanceof BatteryBoxBlock
                || state.getBlock() instanceof ProcessingMachineBlock;
    }
}
