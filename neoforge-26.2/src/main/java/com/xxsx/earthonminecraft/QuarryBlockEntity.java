package com.xxsx.earthonminecraft;

import com.xxsx.earthlore.api.GateResult;
import com.xxsx.earthlore.api.LoreMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * Quarry: the answer to "what if there is no lode here".
 *
 * <p>A bore head is only useful on a deposit. A quarry grinds out a bounded 5x5 work area instead:
 * it reads each block below the preserved support layer and yields that material's ordinary drops.
 * Live deposits consume their finite reserve and become depleted host rock, so the machine does not
 * turn a mineral body into an infinite source.</p>
 *
 * <p>It is deliberately slower and cheaper per unit than a bore head, because it is extracting
 * nothing rarer than the rock itself.</p>
 */
public class QuarryBlockEntity extends BlockEntity implements Container {
    public static final int OUTPUT_SLOTS = 9;
    public static final int TICKS_PER_UNIT = 40;
    public static final int ENERGY_PER_UNIT = 12;
    public static final int WORK_RADIUS = 2;
    /** How far below the quarry it samples to decide what it is cutting. */
    public static final int SAMPLE_DEPTH = 3;

    private final NonNullList<ItemStack> items = NonNullList.withSize(OUTPUT_SLOTS, ItemStack.EMPTY);
    private int progress;
    private int outputCount;
    private boolean active;

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.QUARRY_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuarryBlockEntity quarry) {
        boolean changed = false;

        if (!RealitySupportRules.isOperational(level, pos)) {
            quarry.finishTick(level, pos, state, false);
            return;
        }
        GateResult loreGate = LoreIntegration.checkMachine(quarry, state.getBlock());
        if (loreGate.locked()) {
            LoreMachines.setFault(quarry, "lore_locked", loreGate.missing());
            if (quarry.progress != 0) {
                quarry.progress = 0;
                changed = true;
            }
            quarry.finishTick(level, pos, state, changed);
            return;
        }
        LoreMachines.clearFault(quarry);
        BlockPos target = quarry.targetRock(level);
        if (target == null) {
            quarry.progress = 0;
            quarry.finishTick(level, pos, state, false);
            return;
        }
        quarry.progress++;
        changed = true;

        if (quarry.progress < TICKS_PER_UNIT) {
            quarry.finishTick(level, pos, state, changed);
            return;
        }

        quarry.progress = 0;
        if (quarry.cutBlock(level, target)) {
            changed = true;
        }
        quarry.finishTick(level, pos, state, changed);
    }

    /** Produces one unit of whatever the rock under the quarry yields. */
    private boolean cutBlock(Level level, BlockPos target) {
        BlockState sampled = level.getBlockState(target);
        if (sampled == null || sampled.isAir()) {
            return false;
        }
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return false;
        }
        DepositBlockEntity deposit = level.getBlockEntity(target) instanceof DepositBlockEntity value
                ? value : null;
        List<ItemStack> drops = DepositReserve.rollLoot(server, target, sampled,
                new ItemStack(Items.DIAMOND_PICKAXE));
        if (drops.isEmpty()) {
            return false;
        }
        double yieldFactor = com.xxsx.earthlore.knowledge.KnowledgeService.running()
                ? LoreMachines.yieldFactor(this, sampled.getBlock()) : 1.0;
        List<ItemStack> adjustedDrops = new java.util.ArrayList<>(drops.size());
        for (ItemStack rawDrop : drops) {
            if (rawDrop.isEmpty()) {
                continue;
            }
            int scaledCount = yieldFactor < 1.0
                    ? Math.max(1, (int) Math.floor(rawDrop.getCount() * yieldFactor))
                    : rawDrop.getCount();
            ItemStack adjusted = LoreIntegration.capMachineOutput(this, rawDrop.copyWithCount(scaledCount), getBlockState().getBlock());
            adjustedDrops.add(adjusted);
        }
        drops = adjustedDrops;
        NonNullList<ItemStack> simulated = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            simulated.set(i, items.get(i).copy());
        }
        for (ItemStack drop : drops) {
            if (!MinerBlockEntity.fitInto(simulated, drop.copy())) {
                return false;
            }
        }
        if (!EnergyNetwork.consume(level, worldPosition, ENERGY_PER_UNIT)) {
            progress = TICKS_PER_UNIT - 1;
            return false;
        }
        boolean depletedLode = deposit != null && deposit.reserve().isEmpty();
        if (deposit != null && !depletedLode && deposit.reserve().extract(1) <= 0) {
            return false;
        }
        for (ItemStack drop : drops) {
            insert(drop.copy());
            LoreIntegration.awardMachineProduct(this, drop);
        }
        if (deposit != null && !depletedLode && deposit.reserve().isEmpty()) {
            BlockState depleted = SpecDepletedBlocks.byLode(deposit.reserve().lodeId());
            if (depleted != null) {
                level.setBlock(target, depleted, Block.UPDATE_ALL);
            }
            deposit.syncReserve();
        }
        if (deposit != null) {
            deposit.syncReserve();
        }
        if (deposit == null || depletedLode) {
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    /** Looks a few blocks down for the rock body being quarried. */
    private BlockState sampleRock(Level level) {
        BlockPos target = targetRock(level);
        return target == null ? null : level.getBlockState(target);
    }

    private BlockPos targetRock(Level level) {
        for (int depth = 2; depth <= SAMPLE_DEPTH; depth++) {
            for (int dx = -WORK_RADIUS; dx <= WORK_RADIUS; dx++) {
                for (int dz = -WORK_RADIUS; dz <= WORK_RADIUS; dz++) {
                    BlockPos target = worldPosition.offset(dx, -depth, dz);
                    if (!level.hasChunkAt(target)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(target);
                    if (!state.isAir() && state.getFluidState().isEmpty()
                            && state.getDestroySpeed(level, target) >= 0.0F) {
                        return target;
                    }
                }
            }
        }
        return null;
    }

    private void insert(ItemStack drop) {
        ItemStack remaining = drop.copy();
        for (int i = 0; i < items.size() && !remaining.isEmpty(); i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) {
                items.set(i, remaining);
                remaining = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slot, remaining)) {
                int room = slot.getMaxStackSize() - slot.getCount();
                int moved = Math.min(room, remaining.getCount());
                if (moved > 0) {
                    slot.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }
    }

    private void finishTick(Level level, BlockPos pos, BlockState state, boolean changed) {
        active = progress > 0;
        if (state.hasProperty(QuarryBlock.ACTIVE) && state.getValue(QuarryBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(QuarryBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        if (changed) {
            setChanged();
        }
        outputCount = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                outputCount += stack.getCount();
            }
        }
    }

    public int outputCount() {
        return outputCount;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Progress", progress);
        output.putBoolean("Active", active);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("Progress", 0);
        active = input.getBooleanOr("Active", false);
    }

    // ---- Container ----

    @Override
    public int getContainerSize() {
        return OUTPUT_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            items.set(slot, stack);
            setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }
}
