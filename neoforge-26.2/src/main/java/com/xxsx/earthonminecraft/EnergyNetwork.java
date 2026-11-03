package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EnergyNetwork {
    private static final int MAX_SCAN_NODES = 192;

    private EnergyNetwork() {
    }

    public static boolean consume(Level level, BlockPos consumerPos, int amount) {
        if (amount <= 0) {
            return true;
        }

        List<ElectricEnergyStorage> storages = connectedStorages(level, consumerPos);
        int available = 0;
        for (ElectricEnergyStorage storage : storages) {
            available += storage.extractEnergy(amount - available, true);
            if (available >= amount) {
                break;
            }
        }
        if (available < amount) {
            return false;
        }

        int remaining = amount;
        for (ElectricEnergyStorage storage : storages) {
            remaining -= storage.extractEnergy(remaining, false);
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    public static int extractUpTo(Level level, BlockPos consumerPos, int maxAmount) {
        if (maxAmount <= 0) {
            return 0;
        }
        int remaining = maxAmount;
        for (ElectricEnergyStorage storage : connectedStorages(level, consumerPos)) {
            remaining -= storage.extractEnergy(remaining, false);
            if (remaining <= 0) {
                break;
            }
        }
        return maxAmount - remaining;
    }

    public static int distributeToBuffers(Level level, BlockPos sourcePos, int amount) {
        int remaining = amount;
        for (ElectricEnergyStorage storage : connectedStorages(level, sourcePos)) {
            if (!(storage instanceof BatteryBoxBlockEntity)) {
                continue;
            }
            remaining -= storage.receiveEnergy(remaining, false);
            if (remaining <= 0) {
                break;
            }
        }
        return amount - remaining;
    }

    private static List<ElectricEnergyStorage> connectedStorages(Level level, BlockPos origin) {
        List<ElectricEnergyStorage> storages = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            queue.add(origin.relative(direction));
        }

        while (!queue.isEmpty() && visited.size() < MAX_SCAN_NODES) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ElectricEnergyStorage storage) {
                storages.add(storage);
            }

            if (PowerCableBlock.isCable(level.getBlockState(pos))
                    && RealitySupportRules.isCableOperational(level, pos)) {
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (!visited.contains(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        return storages;
    }
}
